package Info.model;

import java.util.ArrayList;

/**
 * 副本类 - 存储副本信息
 */
public class Replica {
    public int objId; // 对象id
    public int replicaId; // 副本id
    public int diskId; // 磁盘id
    /** 对象分片id -> unitId */
    public ArrayList<Integer> unitIdList;

    public Replica(int objId, int replicaId, int diskId, ArrayList<Integer> unitIdList) {
        this.objId = objId;
        this.replicaId = replicaId;
        this.diskId = diskId;
        this.unitIdList = unitIdList;
    }

    @Override
    public String toString() {
        return "Replica [objId=" + objId + ", replicaId=" + replicaId + ", diskId=" + diskId
                + ", unitIdList=" + unitIdList + "]";
    }
}