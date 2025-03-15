package IO.model;

import java.util.List;

/**
 * 写命令-位置描述
 */
public class DiskUnit {
    public int diskId; // 磁盘ID
    public List<Integer> unitIds; // 单元ID

    public DiskUnit() {
    }

    public DiskUnit(int diskId, List<Integer> unitIds) {
        this.diskId = diskId;
        this.unitIds = unitIds;
    }
}