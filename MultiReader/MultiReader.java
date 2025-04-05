package MultiReader;

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

public class MultiReader {
    private MultiReaderStrategy multiReaderStrategy;

    public MultiReader(String multiReaderStrategy) {
        if (multiReaderStrategy.equals("default")) {
            this.multiReaderStrategy = new DefaultReader();
        } else {
            throw new IllegalArgumentException("Invalid multi reader strategy: " + multiReaderStrategy);
        }
    }

    /**
     * @param readCommandIns 每个时间片段内，所有的读命令
     * @return
     */
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {

        ReadRetrun readRetrun = new ReadRetrun();

        addReadTask(readCommandIns);
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
                    for (int j = 0; j < object.objSize; j++) {

                        // 设置单元中的isInTask为false
                        Info.localDiskTbl.get(diskId).unitData.get(unitIDList.get(j)).isInTask = false;
                        LinkedList<ReadTask> readTasks = object.readTasks;
                        for (ReadTask readTask : readTasks) {
                            if (readTask.blockNotFinished.contains(replica.unitIdList.get(j))) {
                                Info.localDiskTbl.get(diskId).unitData.get(unitIDList.get(j)).isInTask = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return readRetrun;

    }

    // 添加任务
    private void addReadTask(ArrayList<ReadCommandIn> readCommandIns) {
        HashSet<ReadTask> currentTickTasks = new HashSet<>();
        for (ReadCommandIn readCommandIn : readCommandIns) {
            UserObject object = Info.objMap.get(readCommandIn.objId);
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
    }
}