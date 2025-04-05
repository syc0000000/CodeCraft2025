package Info.model;

import java.util.ArrayList;

/**
 * 标签类 - 存储标签信息
 */
public class Tag {
    public int tagId; // 标签id
    public int sizeMax; // 大小
    // Tag随时间的读取量
    public ArrayList<Integer> readSizeByPeriod;
    // Tag随时间总量
    public ArrayList<Integer> totalSizeByPeriod;
    // Tag分配情况(哪些磁盘上有这个tag)
    public ArrayList<Integer> diskIdList;

    @Deprecated
    public ArrayList<Integer> sizeList; // 大小列表，key:DiskId, value:size
    @Deprecated
    public ArrayList<Integer> middleList; // 中间位置列表，key:DiskId, value:middle
    @Deprecated
    public ArrayList<Integer> lenthList; // 规划的区间长度列表，key:DiskId, value:lenth

    public Tag(int tagId, int sizeMax, int disk_num) {
        this.tagId = tagId;
        this.sizeMax = sizeMax;
        this.sizeList = new ArrayList<>(disk_num);
        this.middleList = new ArrayList<>(disk_num);
        this.lenthList = new ArrayList<>(disk_num);
        this.readSizeByPeriod = new ArrayList<>();
        this.totalSizeByPeriod = new ArrayList<>();
        this.diskIdList = new ArrayList<>();
        for (int i = 0; i < disk_num; i++) {
            this.sizeList.add(0);
            this.middleList.add(0);
            this.lenthList.add(0);
        }
    }
}