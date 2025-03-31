package Info.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * 对象类 - 存储对象信息
 */
public class UserObject {
    public int objId; // 对象id
    public int objSize; // 对象大小
    public int objTag; // 对象标签
    public ArrayList<Replica> replicas; // 副本ID到Replica的映射
    /** 存放尚未完成的任务() */
    public LinkedList<ReadTask> readTasks;
    /** 存放过期的任务(task id) */
    public Set<Integer> timeoutTasks = new HashSet<>();

    public UserObject(int objId, int objSize, int objTag) {
        this.objId = objId;
        this.objSize = objSize;
        this.objTag = objTag;
        this.replicas = new ArrayList<>(3);
        this.readTasks = new LinkedList<>();
    }

    public UserObject(int objId, int objSize, int objTag, ArrayList<Replica> replicas) {
        this.objId = objId;
        this.objSize = objSize;
        this.objTag = objTag;
        this.replicas = replicas;
        this.readTasks = new LinkedList<>();
    }

    public void addReplica(Replica replica) {
        replicas.add(replica.replicaId, replica);
    }

    public void addReadTask(ReadTask task) {
        readTasks.add(task);
    }

    /** 过期的任务调用这个方法，从readTask中移除，将TaskId加到timeoutTask中 */
    public void expireTask(ReadTask task) {
        readTasks.remove(task);
        timeoutTasks.add(task.taskId);
    }
}