package Reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedList;
import java.util.Iterator;

import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.UserObject;
import Info.Info.ReadTask;
import Info.Info.DiskSpace;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import IO.model.CompleteCommandOut;

public class BeginStrategy implements ReaderStrategy {
    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        ReadRetrun readRetrun = new ReadRetrun();
        // 添加所有的任务
        addReadTask(readCommandIns);
        // 遍历磁盘
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();
        for (int i = 0; i < Info.diskNum; i++) {
            LocalDisk disk = Info.localDiskTbl.get(i);
            // 有任务read，无任务pass，直到token耗尽
            ReadCommandOut readCommandOut = new ReadCommandOut();
            readCommandOut.actions = new ArrayList<>();
            while (true) {
                int tokenNow = Info.tokenPerTick;
                if (disk.unitData.get(disk.ptr).isInTask) {
                    // read

                    int token = calculateToken(Info.Action.READ, disk);
                    tokenNow -= token;
                    if (tokenNow < 0) {
                        break;
                    }
                    readCommandOut.actions.add(Info.Action.READ);
                    disk.pretoken = token;
                    disk.preoper = Info.Action.READ;
                    int ptr = disk.ptr;
                    int objId = disk.ptrDoAction(Info.Action.READ);
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
                } else {
                    // pass
                    int token = calculateToken(Info.Action.PASS, disk);
                    tokenNow -= token;
                    if (tokenNow < 0) {
                        break;
                    }
                    readCommandOut.actions.add(Info.Action.PASS);
                    disk.preoper = Info.Action.PASS;
                    disk.pretoken = token;
                    disk.ptrDoAction(Info.Action.PASS);
                }
            }
            readCommandOuts.put(i, readCommandOut);
        }
        readRetrun.readCommandOuts = readCommandOuts;
        readRetrun.completeCommandOuts = completeCommandOuts;
        return readRetrun;
    }
}
