package Info.model;

/**
 * 对象-块信息类 - 存储对象的块信息
 */
public class UnitData {
    public int objId; // 对象id
    /** 对象的块id，表示是对象的第几块 */
    public int blockId;
    public DiskSpace space; // 空间
    public boolean isInTask;

    public UnitData(int objId, int blockId, DiskSpace space) {
        this.objId = objId;
        this.blockId = blockId;
        this.space = space;
        this.isInTask = false;
    }
}