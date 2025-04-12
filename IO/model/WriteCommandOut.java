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

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(objId).append("\n");

        sb.append(copy1.diskId + 1);
        for (int unitId : copy1.unitIds) {
            sb.append(" ").append(unitId + 1);
        }
        sb.append("\n");

        sb.append(copy2.diskId + 1);
        for (int unitId : copy2.unitIds) {
            sb.append(" ").append(unitId + 1);
        }
        sb.append("\n");

        sb.append(copy3.diskId + 1);
        for (int unitId : copy3.unitIds) {
            sb.append(" ").append(unitId + 1);
        }
        sb.append("\n");

        return sb.toString();
    }
}