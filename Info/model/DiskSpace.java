package Info.model;

import java.util.Comparator;
import Info.Info;

/**
 * 磁盘空间类 - 表示空闲或占用的空间
 */
public class DiskSpace {
    public boolean isFree; // true:空闲，false:占用
    public int start; // 空间起点
    public int end; // 空间终点
    public int size; // 空间大小(缓存以避免重复计算)
    public int sizeInMap; // 空间大小(用于Map的key)
    public int diskId; // 所属磁盘ID
    public int tagId; // 所属tagId

    @Deprecated
    public DiskSpaceType type; // 空间类型

    // 定义Comparator，按start升序排序
    public static Comparator<DiskSpace> comparator = new Comparator<DiskSpace>() {
        @Override
        public int compare(DiskSpace o1, DiskSpace o2) {
            return o1.start - o2.start;
        }
    };

    public DiskSpace(boolean isFree, int start, int end, int diskId) {
        this.isFree = isFree;
        this.start = start;
        this.end = end;
        this.size = end - start + 1;
        this.diskId = diskId;
        this.type = DiskSpaceType.UNUSED;
        this.sizeInMap = size > 5 ? 5 : size;
    }

    public DiskSpace(boolean isFree, int start, int end, int diskId, int tagId) {
        this(isFree, start, end, diskId);
        this.tagId = tagId;
    }

    @Override
    public String toString() {
        return "DiskSpace[disk=" + diskId + ", 区间 [" + start + ", " + end + "], size=" + size
                + ", isFree=" + isFree + "], tagId=" + tagId;
    }

    public void setStartAndEnd(int start, int end) {
        this.start = start;
        this.end = end;
        this.size = end - start + 1;
        this.sizeInMap = size > 5 ? 5 : size;
    }
}