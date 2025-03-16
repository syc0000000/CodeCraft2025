package Reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.CompleteCommandOut;
import IO.model.ReadRetrun;
import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.ReadTask;
import Info.Info.Replica;
import Info.Info.UserObject;
import Info.Info.DiskSpace;

public class ReadOnlyStrategy implements ReaderStrategy {

    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        ReadRetrun readRetrun = new ReadRetrun();
        // 填入ReadTask
        for (ReadCommandIn readCommandIn : readCommandIns) {
            ReadTask readTask = new ReadTask(readCommandIn.commandId, readCommandIn.objId);
            Info.readTaskTbl.put(readCommandIn.commandId, readTask);
            // readerLogger.debug("加入objTaskMap: " + readCommandIn.objId + " " +
            // readCommandIn.commandId);
            if (Info.objTaskMap.get(readCommandIn.objId) == null) {
                HashSet<Integer> readTaskSet = new HashSet<>();
                readTaskSet.add(readCommandIn.commandId);
                Info.objTaskMap.put(readCommandIn.objId, readTaskSet);
            } else {
                Info.objTaskMap.get(readCommandIn.objId).add(readCommandIn.commandId);
            }
        }
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
                while (true) {
                    // 计算读取操作消耗的token
                    int token = calculateToken(Info.Action.READ, disk);
                    tokenNow -= token;
                    // readerLogger.debug("token_spend: " + token + ", token_now: " + tokenNow);
                    if (tokenNow < 0) {
                        break;
                    }
                    readCommandOut.actions.add(Info.Action.READ);
                    disk.pretoken = token;
                    disk.preoper = Info.Action.READ;
                    int objId = disk.ptrDoAction(Info.Action.READ);
                    UserObject obj = Info.objMap.get(objId);
                    DiskSpace space = disk.getSpaceForUnit(disk.ptr);
                    // readerLogger.debug("space: " + space);

                    if (objId != -1) {
                        // 检测完成
                        // readerLogger.debug("objId: " + objId);
                        // 遍历unit list 获取这一格是obj的第几个分片
                        int blockId = disk.unitData.get(disk.ptr).blockId;
                        HashSet<Integer> taskSet = Info.objTaskMap.get(objId);
                        if (taskSet != null) {
                            for (Integer task : taskSet) {
                                ReadTask readTask = Info.readTaskTbl.get(task);
                                readTask.blockFinished.add(blockId);
                                readTask.blockNotFinished.remove(blockId);
                                if (readTask.blockNotFinished.isEmpty()) {
                                    // 任务完成
                                    readerLogger.debug("任务完成: " + readTask.taskId);
                                    completeCommandOuts.add(new CompleteCommandOut(readTask.taskId));
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
