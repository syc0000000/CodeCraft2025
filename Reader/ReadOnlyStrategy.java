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
            if (Info.objTaskMap.get(readCommandIn.objId) == null) {
                HashSet<ReadTask> readTaskSet = new HashSet<>();
                readTaskSet.add(readTask);
                Info.objTaskMap.put(readCommandIn.objId, readTaskSet);
            } else {
                Info.objTaskMap.get(readCommandIn.objId).add(readTask);
            }
        }
        // 每TickToken
        int tickToken = Info.tokenPerTick;
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        ArrayList<CompleteCommandOut> completeCommandOuts = new ArrayList<>();
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
                    if (token > tokenNow) {
                        break;
                    }
                    readCommandOut.actions.add(Info.Action.READ);
                    tokenNow -= token;
                    disk.pretoken = token;
                    disk.preoper = Info.Action.READ;
                    int objId = disk.ptrDoAction(Info.Action.READ);
                    UserObject obj = Info.objMap.get(objId);
                    DiskSpace space = disk.getSpaceForUnit(disk.ptr);

                    if (objId != -1) {
                        // 检测完成
                        // 遍历unit list 获取这一格是obj的第几个分片
                        int blockId = 0;
                        for (int blockNow = 0; blockNow < obj.objSize; blockNow++) {
                            if (space.replica.unitIdList.get(blockNow) == disk.ptr) {
                                blockId = blockNow;
                                break;
                            }
                        }
                        HashSet<ReadTask> taskSet = Info.objTaskMap.get(objId);
                        for (ReadTask task : taskSet) {
                            task.blockFinished.add(blockId);
                            task.blockNotFinished.remove(blockId);
                            if (task.blockNotFinished.isEmpty()) {
                                // 任务完成
                                completeCommandOuts.add(new CompleteCommandOut(task.taskId));
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
