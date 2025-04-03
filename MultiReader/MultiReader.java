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
    /** 存放当前[tick-105, tick]时间片内未完成的读取任务的id，对于tick-105到达的任务, 最晚要在tick上报 */
    public LinkedList<HashSet<Integer>> readTasksInRecent105Tick = new LinkedList<>();

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

        ReadCommandOut readCommandOut = this.multiReaderStrategy.read();

        readTasksInRecent105Tick.add(currentTickTasks);
        if (readTasksInRecent105Tick.size() > 105) {
            HashSet<Integer> tasksToAbort = readTasksInRecent105Tick.remove(0);
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