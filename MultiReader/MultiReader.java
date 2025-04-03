package MultiReader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import IO.model.ReadCommandIn;
import IO.model.ReadRetrun;
import Info.Info;
import Info.model.UserObject;
import Info.model.ReadTask;
import Info.model.Replica;
import IO.model.ReadCommandOut;

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
        HashSet<Integer> currentTickTasks = new HashSet<>();
        for (ReadCommandIn cmd : readCommandIns) {
            currentTickTasks.add(cmd.commandId);
        }
        addReadTask(readCommandIns);

        MultiReadCommandOut readCommandOut = this.multiReaderStrategy.read();

        Info.readTasksInRecent105Tick.add(currentTickTasks);
        if (Info.readTasksInRecent105Tick.size() > 105) {
            HashSet<Integer> tasksToAbort = Info.readTasksInRecent105Tick.remove(0);
            // TODO 删除任务，另外，deleter是不是也要处理这里的信息
        }
        return null;

    }

    // 添加任务
    private void addReadTask(ArrayList<ReadCommandIn> readCommandIns) {
        for (ReadCommandIn readCommandIn : readCommandIns) {
            UserObject object = Info.objMap.get(readCommandIn.objId);
            ReadTask readTask = new ReadTask(readCommandIn.commandId, readCommandIn.objId, object.objSize);
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
    }
}