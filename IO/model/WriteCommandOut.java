package IO.model;

/**
 * 写命令输出-选手
 */
public class WriteCommandOut {
    public int objId; // 对象ID
    public DiskUnit copy1; // 副本1的存放位置
    public DiskUnit copy2; // 副本2的存放位置
    public DiskUnit copy3; // 副本3的存放位置

    public WriteCommandOut() {
    }

    public WriteCommandOut(int objId, DiskUnit copy1, DiskUnit copy2, DiskUnit copy3) {
        this.objId = objId;
        this.copy1 = copy1;
        this.copy2 = copy2;
        this.copy3 = copy3;
    }
}