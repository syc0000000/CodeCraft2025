package Reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.CompleteCommandOut;
import IO.model.ReadRetrun;
import Info.Info;
import Info.model.LocalDisk;
import Info.model.ReadTask;
import Info.model.UserObject;
import Info.model.Action;

public class ReadOnlyStrategy implements ReaderStrategy {

    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        ReadRetrun readRetrun = new ReadRetrun();
        // 添加所有的任务
        addReadTask(readCommandIns);
        // 每TickToken
        int tickToken = Info.tokenPerTick;
        // readerLogger.debug("tokenPerTick: " + tickToken);
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();
        // 遍历磁盘
        for (int i = 0; i < Info.diskNum; i++) {
            LocalDisk disk = Info.localDiskTbl.get(i);
            ReadCommandOut readCommandOut = new ReadCommandOut();
            readCommandOut.actions = new ArrayList<>();
            int tokenNow = tickToken;
            if (disk != null) {
                boolean ifJump = false;
                if (disk.ptr > disk.RWEnd) {
                    readCommandOut.actions.add(Action.JUMP);
                    readCommandOut.jumpTarget = 0;
                    disk.ptrDoAction(Action.JUMP, 0);
                    disk.preoper = Action.JUMP;
                    disk.pretoken = Info.tokenPerTick;
                    ifJump = true;
                }
                while (!ifJump) {
                    // 如果磁头指针大于RWEnd，则跳回开头
                    if (disk.ptr > disk.RWEnd) {
                        break;
                    }
                    // 计算读取操作消耗的token
                    int token = calculateToken(Action.READ, disk);
                    tokenNow -= token;
                    // readerLogger.debug("token_spend: " + token + ", token_now: " + tokenNow);
                    if (tokenNow < 0) {
                        break;
                    }
                    readCommandOut.actions.add(Action.READ);
                    disk.pretoken = token;
                    disk.preoper = Action.READ;
                    int ptr = disk.ptr;
                    int objId = disk.ptrDoAction(Action.READ);
                    UserObject obj = Info.objMap.get(objId);
                    // readerLogger.debug("space: " + space);

                    if (objId != -1) {
                        // 检测完成
                        readerLogger.debug("objId: " + objId);
                        // 遍历unit list 获取这一格是obj的第几个分片
                        int blockId = disk.unitData.get(ptr).blockId;
                        LinkedList<ReadTask> taskSet = obj.readTasks;
                        if (taskSet != null) {
                            Iterator<ReadTask> iterator = taskSet.iterator();
                            while (iterator.hasNext()) {
                                ReadTask readTask = iterator.next();
                                // 检测过期
                                if (readTask.isTimeout()) {
                                    // 任务完成
                                    readerLogger.debug("任务过期: " + readTask.taskId);
                                    obj.timeoutTasks.add(readTask.taskId);
                                    iterator.remove();
                                    continue;
                                }
                                boolean addSucc = readTask.blockFinished.add(blockId);
                                boolean removeSucc = readTask.blockNotFinished.remove(blockId);
                                readerLogger.debug("taskId: " + readTask.taskId + "完成块: " + blockId + " addSucc: "
                                        + addSucc + ", removeSucc: " + removeSucc);
                                if (readTask.blockNotFinished.isEmpty()) {
                                    // 任务完成
                                    readerLogger.debug("任务完成: " + readTask.taskId);
                                    completeCommandOuts.add(new CompleteCommandOut(readTask.taskId));
                                    // 使用迭代器安全地删除元素
                                    iterator.remove();
                                }
                            }
                        }
                    }
                }
            }
            readCommandOuts.put(i, readCommandOut);
        }
        readRetrun.readCommandOuts = readCommandOuts;
        readRetrun.completeCommandOuts = completeCommandOuts;
        return readRetrun;
    }
}
