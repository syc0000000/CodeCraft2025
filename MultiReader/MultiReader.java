package MultiReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import IO.model.ReadCommandIn;
import IO.model.ReadRetrun;
import IO.model.CompleteCommandOut;
import Info.Info;
import Info.model.UserObject;
import Info.model.ReadTask;
import Info.model.Replica;
import IO.model.MultiReadCommandOut;
import IO.model.BusyCommandOut;

public class MultiReader {
    private MultiReaderStrategy multiReaderStrategy;

    public MultiReader(String multiReaderStrategy) {
        if (multiReaderStrategy.equals("default")) {
            this.multiReaderStrategy = new DefaultReader();
        } else if (multiReaderStrategy.equals("readonly")) {
            this.multiReaderStrategy = new ReadOnlyReader();
        } else if (multiReaderStrategy.equals("range")) {
            this.multiReaderStrategy = new RangeReader(Info.periodToTagSet);
        } else {
            throw new IllegalArgumentException("Invalid multi reader strategy: " + multiReaderStrategy);
        }
    }

    /**
     * @param readCommandIns 每个时间片段内，所有的读命令
     * @return
     */
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        HashSet<ReadTask> earlyBusyTasks = new HashSet<>();
        ReadRetrun readRetrun = new ReadRetrun();
        // long addtaskstart = System.nanoTime();
        earlyBusyTasks = addReadTask(readCommandIns);
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();
        // 得到每一块硬盘的输出以及完成的命令

        for (int i = 0; i < Info.diskNum; i++) {
            MultiReadCommandOut readCommandOut = new MultiReadCommandOut();
            this.multiReaderStrategy.read(i, readCommandOut, completeCommandOuts);
            readRetrun.readCommandOuts.put(i, readCommandOut);
        }
        if (Info.readTasksInRecent105Tick.size() > 105) {
            HashSet<ReadTask> tasksToAbort = Info.readTasksInRecent105Tick.remove(0);
            // TODO 删除任务，另外，deleter是不是也要处理这里的信息
            // 这里根据readtask寻找到objid，从objid中寻找到task列表并移除
            if (tasksToAbort != null) {
                HashSet<Integer> objIdSet = new HashSet<>();
                for (ReadTask readTask : tasksToAbort) {
                    objIdSet.add(readTask.objId);
                    UserObject object = Info.objMap.get(readTask.objId);
                    object.readTasks.remove(readTask);
                }
                for (Integer objId : objIdSet) {
                    UserObject object = Info.objMap.get(objId);
                    Replica replica = object.replicas.get(0);
                    int diskId = replica.diskId;
                    ArrayList<Integer> unitIDList = replica.unitIdList;
                    // 刷新Obj的isInTask情况
                    // 先全部清空
                    for (int j = 0; j < object.objSize; j++) {
                        // 设置单元中的isInTask为false
                        Info.localDiskTbl.get(diskId).unitData.get(unitIDList.get(j)).isInTask = false;
                    }
                    LinkedList<ReadTask> readTasks = object.readTasks;
                    // 然后根据readtask中的blockNotFinished情况，设置isInTask为true
                    for (ReadTask readTask : readTasks) {
                        for (int blockId : readTask.blockNotFinished) {
                            Info.localDiskTbl.get(diskId).unitData.get(unitIDList.get(blockId)).isInTask = true;
                        }
                    }
                }
            }
            // busyCommandOuts
            ArrayList<BusyCommandOut> busyCommandOuts = new ArrayList<>();
            for (ReadTask readTask : tasksToAbort) {
                BusyCommandOut busyCommandOut = new BusyCommandOut(readTask.taskId);
                busyCommandOuts.add(busyCommandOut);
            }
            readRetrun.busyCommandOuts = busyCommandOuts;
        }
        // 处理earlyBusyTasks
        for (ReadTask readTask : earlyBusyTasks) {
            BusyCommandOut busyCommandOut = new BusyCommandOut(readTask.taskId);
            readRetrun.busyCommandOuts.add(busyCommandOut);
        }
        readRetrun.completeCommandOuts = completeCommandOuts;

        return readRetrun;

    }

    // 添加任务
    // 注意注意：返回值是当前tick需要报Busy的task，新的task已经直接加进去Info了
    private HashSet<ReadTask> addReadTask(ArrayList<ReadCommandIn> readCommandIns) {
        HashSet<ReadTask> earlyBusyTasks = new HashSet<>();
        HashSet<ReadTask> currentTickTasks = new HashSet<>();
        for (ReadCommandIn readCommandIn : readCommandIns) {
            UserObject object = Info.objMap.get(readCommandIn.objId);
            if (multiReaderStrategy instanceof RangeReader) {
                // earlyBusy
                int period = Info.timestamp / 1800;
                if (period >= Info.periodToTagSet.size()) {
                    period = Info.periodToTagSet.size() - 1;
                }
                if (1800 * (period + 1) - Info.timestamp > 50) {
                    // 如果不是马上要换period，都不添加其他tag的任务
                    if (!Info.periodToTagSet.get(period).contains(object.objTag)) {
                        ReadTask readTask = new ReadTask(readCommandIn.commandId, readCommandIn.objId, object.objSize);
                        earlyBusyTasks.add(readTask);
                        continue;
                    }
                }
            }
            ReadTask readTask = new ReadTask(readCommandIn.commandId, readCommandIn.objId, object.objSize);
            currentTickTasks.add(readTask);
            object.readTasks.add(readTask);
            for (int i = 0; i < 3; i++) {
                // 找到对应的副本
                Replica replica = object.replicas.get(i);
                int diskId = replica.diskId;
                ArrayList<Integer> unitIDList = replica.unitIdList;
                for (int j = 0; j < object.objSize; j++) {
                    // 设置单元中的isInTask为true
                    Info.localDiskTbl.get(diskId).unitData.get(unitIDList.get(j)).isInTask = true;
                }
            }
        }
        Info.readTasksInRecent105Tick.add(currentTickTasks);
        return earlyBusyTasks;
    }
}