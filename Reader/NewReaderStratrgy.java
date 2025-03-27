package Reader;

import java.util.ArrayList;

import IO.model.CompleteCommandOut;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import Info.Info.UserObject;
import Reader.SequenceOptimizer.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import Info.Info;
import Info.Info.Action;
import Info.Info.LocalDisk;
import Info.Info.ReadTask;
import Info.Info.UserObject;

public class NewReaderStratrgy implements ReaderStrategy {
    private static final int[] R_COSTS = { 64, 52, 42, 34, 28, 23, 19, 16 };

    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        // TODO: 实现默认的读取策略
        ReadRetrun readRetrun = new ReadRetrun();
        // 添加所有的任务
        addReadTask(readCommandIns);
        int tickToken = Info.tokenPerTick;
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();
        // readerLogger.debug("进入读取模块");
        // 遍历磁盘
        for (int i = 0; i < Info.diskNum; i++) {
            // 基础准备
            LocalDisk disk = Info.localDiskTbl.get(i);
            int tokenNow = tickToken;
            ReadCommandOut readCommandOut = new ReadCommandOut();
            if (disk.ptr > disk.RWEnd) {
                readCommandOut.actions.add(Info.Action.JUMP);
                disk.preoper = Info.Action.JUMP;
                disk.pretoken = 0;
                readCommandOut.jumpTarget = 0;
                disk.ptr = 0;
                readCommandOuts.put(i, readCommandOut);
                // readerLogger.debug("磁盘编号" + i + "已经跳转");
                continue;
            }
            // 准备优化的读取序列
            ArrayList<Action> sequence = new ArrayList<>();
            int k = 0; // 向前加入READ序列
            int tokencpy = tokenNow; // token的拷贝
            int tokenRead = 0; // 在这个序列中已经使用过的token数量
            int newtoken = 0; // 新的token
            int pasttoken = 1; // 上一次的token
            switch (disk.pretoken) {
                case 64:
                    k = 1;
                    sequence.add(Info.Action.READ);
                    break;
                case 52:
                    k = 2;
                    for (int temp = 0; temp < k; temp++) {
                        sequence.add(Info.Action.READ);
                    }
                    break;
                case 42:
                    k = 3;
                    for (int temp = 0; temp < k; temp++) {
                        sequence.add(Info.Action.READ);
                    }
                    break;
                case 34:
                    k = 4;
                    for (int temp = 0; temp < k; temp++) {
                        sequence.add(Info.Action.READ);
                    }
                    break;
                case 28:
                    k = 5;
                    for (int temp = 0; temp < k; temp++) {
                        sequence.add(Info.Action.READ);
                    }
                    break;
                case 23:
                    k = 6;
                    for (int temp = 0; temp < k; temp++) {
                        sequence.add(Info.Action.READ);
                    }
                    break;
                case 19:
                    k = 7;
                    for (int temp = 0; temp < k; temp++) {
                        sequence.add(Info.Action.READ);
                    }
                    break;
                case 16:
                    k = 8;
                    for (int temp = 0; temp < k; temp++) {
                        sequence.add(Info.Action.READ);
                    }
                    break;
                default:
                    break;
            }
            // 将额外操作的token加入
            for (int temp = 0; temp < k; temp++) {
                tokenRead += R_COSTS[temp];
            }
            //添加从目前位置向后的序列
            int pretoken = disk.pretoken; //手动添加序列操作中，上一次的token
            Action preoper = disk.preoper; //手动添加序列操作中，上一次的操作
            int sequenceptr = 0; //手动添加序列操作中，用到的指针
            int lastReadToken = disk.pretoken; //用于记录上一次的读操作使用的token，用于去掉末尾的pass
            Result result = new Result();
            while (newtoken != pasttoken) {
                // 添加未优化路径
                while (tokencpy > 0) {
                    if (disk.unitData.get(disk.ptr + sequenceptr).isInTask) {
                        int tokenIsToUse = calculateToken(Info.Action.READ, preoper, pretoken);
                        if (tokencpy - tokenIsToUse < 0) {
                            break;
                        }
                        pretoken = tokenIsToUse;
                        sequence.add(Info.Action.READ);
                        tokencpy -= pretoken;
                        preoper = Info.Action.READ;
                        lastReadToken = pretoken;
                    }
                    else{
                        int tokenIsToUse = calculateToken(Info.Action.PASS, preoper, pretoken);
                        if (tokencpy - tokenIsToUse < 0) {
                            break;
                        }
                        pretoken = tokenIsToUse;
                        sequence.add(Info.Action.PASS);
                        tokencpy -= pretoken;
                        preoper = Info.Action.PASS;
                    }

                    sequenceptr++;
                }
                pasttoken = tokenRead + tokenNow - tokencpy;
                result = SequenceOptimizer.optimizeSequence(sequence);
                newtoken = result.cost;
                tokencpy += pasttoken - newtoken;
            }
            //readerLogger.debug("tokencpy"+tokencpy);
            //将最优序列添加到输出中
            while(sequence.size() > 0 && sequence.get(sequence.size() - 1) == Info.Action.PASS){
                sequence.remove(sequence.size() - 1);
                sequenceptr--;
            }
            for(int temp = k; temp < k + sequenceptr; temp++){
                readCommandOut.actions.add(result.sequence.get(temp));
            }
            for (int j = 0; j < readCommandOut.actions.size(); j++) {
                if (disk.unitData.get(disk.ptr + j).isInTask) {
                    disk.unitData.get(disk.ptr + j).isInTask = false;
                    int blockId = disk.unitData.get(disk.ptr + j).blockId;
                    int objId = disk.unitData.get(disk.ptr + j).objId;
                    UserObject obj = Info.objMap.get(objId);
                    // readerLogger.debug("ptr为"+(disk.ptr+j)+"已读到objid为" + objId + " obj为" + obj +
                    // " blockid为" + blockId);
                    Iterator<ReadTask> iterator = obj.readTasks.iterator();
                    while (iterator.hasNext()) {
                        ReadTask readTask = iterator.next();
                        if (readTask.blockNotFinished.contains(blockId)) {
                            // 检测任务的完成
                            // 检测过期
                            // readerLogger.debug("任务ID，objid，blockid为" + readTask.taskId + "," + objId +
                            // "," + blockId);
                            // readerLogger.debug("任务是否完成"+readTask.blockNotFinished.isEmpty());
                            if (readTask.isTimeout()) {
                                // readerLogger.debug("任务过期: " + readTask.taskId);
                                obj.timeoutTasks.add(readTask.taskId);
                                iterator.remove();
                                continue;
                            }
                            // 处理块
                            readTask.blockNotFinished.remove(blockId);
                            // readerLogger.debug("任务ID"+readTask.taskId+"没有读的块"+readTask.blockNotFinished.size());
                            readTask.blockFinished.add(blockId);
                            // readerLogger.debug("任务是否完成"+readTask.blockNotFinished.isEmpty());
                            if (readTask.blockNotFinished.isEmpty()) {
                                // readerLogger.debug("上报任务id " + readTask.taskId + " objID" + objId);
                                completeCommandOuts.add(new CompleteCommandOut(readTask.taskId));
                                iterator.remove();
                            }
                        }
                    }
                }
            }
            //更新硬盘信息
            
            if(sequenceptr != 0){
                disk.preoper = result.sequence.get(result.sequence.size() - 1);
            }
            disk.pretoken = lastReadToken;
            disk.ptr += sequenceptr;

            // 如果检测到需要跳转，则直接跳转
            // readerLogger.debug("磁盘编号" + i + "目前ptr位置为" + disk.ptr + "RWEnd位置为" +
            // disk.RWEnd);
            readCommandOuts.put(i, readCommandOut);
        }
        readRetrun.readCommandOuts = readCommandOuts;
        readRetrun.completeCommandOuts = completeCommandOuts;
        return readRetrun;
    }

    public int calculateToken(Info.Action action, Action preoper, int pretoken) {
        switch (action) {
            case READ:
                // 向上取整
                // readerLogger.debug("计算token: pretoken=" + disk.pretoken);
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

}
