package Reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.UserObject;
import Info.Info.ReadTask;
import Info.Info.DiskSpace;
import Info.Info.Action;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import IO.model.CompleteCommandOut;
import Reader.SequenceOptimizer.Result;

public class BeginStrategy implements ReaderStrategy {
    private static final ArrayList<Integer> R_COSTS = new ArrayList<>(Arrays.asList(64, 52, 42, 34, 28, 23, 19, 16));
    // 创建线程池，使用可用处理器数量
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        ReadRetrun readRetrun = new ReadRetrun();
        // 添加所有的任务
        addReadTask(readCommandIns);

        // 使用ConcurrentHashMap替代HashMap，用于多线程安全
        Map<Integer, ReadCommandOut> readCommandOuts = new ConcurrentHashMap<>();
        // 使用ConcurrentHashMap的线程安全集合，稍后会转换为HashSet返回
        Set<CompleteCommandOut> concurrentCompleteCommandOuts = ConcurrentHashMap.newKeySet();

        // 创建用于收集每个磁盘处理结果的Future列表
        ArrayList<Future<Void>> futures = new ArrayList<>();

        // 为每个磁盘创建一个任务
        for (int i = 0; i < Info.diskNum; i++) {
            final int diskId = i;
            // 提交任务到线程池
            futures.add(executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    processDisk(diskId, readCommandOuts, concurrentCompleteCommandOuts);
                    return null;
                }
            }));
        }

        // 等待所有任务完成
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 将并发集合转换为HashSet以匹配ReadRetrun的要求
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>(concurrentCompleteCommandOuts);

        readRetrun.readCommandOuts = readCommandOuts;
        readRetrun.completeCommandOuts = completeCommandOuts;
        return readRetrun;
    }

    /**
     * 处理单个磁盘的方法，被多线程并行调用
     */
    private void processDisk(int diskId, Map<Integer, ReadCommandOut> readCommandOuts,
            Set<CompleteCommandOut> completeCommandOuts) {
        // 基础准备
        LocalDisk disk = Info.localDiskTbl.get(diskId);
        int tokenTotal = Info.tokenPerTick;
        ReadCommandOut readCommandOut = new ReadCommandOut();
        if (disk.ptr > disk.RWEnd) {
            readCommandOut.actions.add(Info.Action.JUMP);
            disk.preoper = Info.Action.JUMP;
            disk.pretoken = 0;
            readCommandOut.jumpTarget = 0;
            disk.ptr = 0;
            readCommandOuts.put(diskId, readCommandOut);
            return;
        }
        // 生成初始序列
        ArrayList<Action> sequence = new ArrayList<>();
        int oldCost = Integer.MAX_VALUE;
        int newCost = 0;
        int k = 0;
        // 如果preoper为Read，根据pretoken算出前面有多少个READ，如果不是初始序列就为空
        if (disk.preoper == Info.Action.READ) {
            k = R_COSTS.indexOf(disk.pretoken);
            k++;
            for (int j = 0; j < k; j++) {
                sequence.add(Info.Action.READ);
                // 将额外操作的token加入
                tokenTotal += R_COSTS.get(j);
                newCost += R_COSTS.get(j);
            }
        }
        // 开始动归优化
        Action preoper = disk.preoper;
        int pretoken = disk.pretoken;
        int sequenceptr = 0;
        Result result = new Result();
        int i = 0;// 优化轮数
        while (newCost != oldCost && i < 3) {
            // 新开销
            oldCost = newCost;
            // 计算当前还剩多少token
            int leftToken = tokenTotal - newCost;

            while (leftToken > 0) {
                // 往后续路径，添加操作
                if (disk.unitData.get(disk.ptr + sequenceptr).isInTask) {
                    int tokenIsToUse = calculateToken(Info.Action.READ, preoper, pretoken);
                    if (leftToken - tokenIsToUse < 0) {
                        break;
                    }
                    pretoken = tokenIsToUse;
                    sequence.add(Info.Action.READ);
                    leftToken -= pretoken;
                    preoper = Info.Action.READ;
                } else {
                    int tokenIsToUse = calculateToken(Info.Action.PASS, preoper, pretoken);
                    if (leftToken - tokenIsToUse < 0) {
                        break;
                    }
                    pretoken = tokenIsToUse;
                    sequence.add(Info.Action.PASS);
                    leftToken -= pretoken;
                    preoper = Info.Action.PASS;
                }
                sequenceptr++;
            }
            result = SequenceOptimizer.optimizeSequence(sequence);
            newCost = result.cost;
            i++;
        }
        // while (sequence.size() > 0 && sequence.get(sequence.size() - 1) ==
        // Info.Action.PASS) {
        // sequence.remove(sequence.size() - 1);
        // sequenceptr--;
        // }
        for (int temp = k; temp < k + sequenceptr; temp++) {
            readCommandOut.actions.add(result.sequence.get(temp));
        }
        for (int j = 0; j < readCommandOut.actions.size(); j++) {
            if (disk.unitData.get(disk.ptr + j).isInTask) {
                disk.unitData.get(disk.ptr + j).isInTask = false;
                int blockId = disk.unitData.get(disk.ptr + j).blockId;
                int objId = disk.unitData.get(disk.ptr + j).objId;
                UserObject obj = Info.objMap.get(objId);
                synchronized (obj) { // 对对象进行同步，防止多线程同时修改
                    Iterator<ReadTask> iterator = obj.readTasks.iterator();
                    while (iterator.hasNext()) {
                        ReadTask readTask = iterator.next();
                        if (readTask.blockNotFinished.contains(blockId)) {
                            if (readTask.isTimeout()) {
                                obj.timeoutTasks.add(readTask.taskId);
                                iterator.remove();
                                continue;
                            }
                            // 处理块
                            readTask.blockNotFinished.remove(blockId);
                            readTask.blockFinished.add(blockId);
                            if (readTask.blockNotFinished.isEmpty()) {
                                completeCommandOuts.add(new CompleteCommandOut(readTask.taskId));
                                iterator.remove();
                            }
                        }
                    }
                }
            }
        }
        // 更新硬盘信息
        if (sequenceptr != 0) {
            disk.preoper = result.sequence.get(result.sequence.size() - 1);
        }
        disk.pretoken = pretoken;
        disk.ptr += sequenceptr;

        readCommandOuts.put(diskId, readCommandOut);
    }

    public int calculateToken(Info.Action action, Action preoper, int pretoken) {
        switch (action) {
            case READ:
                // 向上取整
                int token;
                if (preoper == action.READ) {
                    token = (int) Math.ceil(pretoken * 0.8);
                } else {
                    token = 64;
                }
                return token < 16 ? 16 : token;
            case JUMP:
                return Info.tokenPerTick;
            case PASS:
                return 1;
            default:
                return -1;// 异常
        }
    }

    // 在类对象被GC回收前关闭线程池
    @Override
    protected void finalize() throws Throwable {
        try {
            executor.shutdown();
        } finally {
            super.finalize();
        }
    }
}
