package MultiReader;

import java.util.ArrayList;
import java.util.HashSet;
import IO.model.ReadCommandIn;
import IO.model.ReadRetrun;

public class MultiReader {
    private MultiReaderStrategy multiReaderStrategy;
    /** 存放当前[tick-105, tick]时间片内未完成的读取任务的id，对于tick-105到达的任务, 最晚要在tick上报 */
    public ArrayList<HashSet<Integer>> readTasksInRecent105Tick = new ArrayList<>();

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

        // this.multiReaderStrategy.read();

        readTasksInRecent105Tick.add(currentTickTasks);
        if (readTasksInRecent105Tick.size() > 105) {
            HashSet<Integer> tasksToAbort = readTasksInRecent105Tick.remove(0);
        }
        return null;

    }
}