package IO.model;

import java.util.List;

/**
 * DiskUnit用于描述对象的某一个副本写入的位置
 * 
 * @example <code>DiskUnit diskUnit = new DiskUnit(0, List.of(1, 2, 3));</code> 
 * 表示副本写入到磁盘0的1, 2, 3单元
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