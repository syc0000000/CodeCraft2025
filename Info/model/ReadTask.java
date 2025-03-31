package Info.model;

import java.util.HashSet;
import java.util.Set;
import Info.Info;

/**
 * 读任务类 - 存储读任务信息
 */
public class ReadTask {
    // 任务属性
    public int taskId; // 任务id
    public int startTime; // 任务开始时间
    public int taskValue; // 任务价值

    // 任务内容
    public int objId; // 对象id
    public int objSize; // 对象大小
    public int objTag; // 对象标签
    public Set<Integer> blockFinished; // 已经完成的块
    public Set<Integer> blockNotFinished; // 未完成的块

    public ReadTask(int taskId, int objId) {
        this.taskId = taskId;
        this.objId = objId;
        this.startTime = Info.timestamp;
        this.taskValue = 0;
        this.blockFinished = new HashSet<>();
        this.blockNotFinished = new HashSet<>();
    }

    public ReadTask(int taskId, int objId, int objsize) {
        this.taskId = taskId;
        this.objId = objId;
        this.startTime = Info.timestamp;
        this.taskValue = 0;
        this.blockFinished = new HashSet<>();
        this.blockNotFinished = new HashSet<>();
        for (int i = 0; i < objsize; i++) {
            this.blockNotFinished.add(i);
        }
    }

    public boolean isTimeout() {
        return Info.timestamp - startTime > 106;
    }
}