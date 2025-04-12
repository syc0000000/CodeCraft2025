package Writer.GA;

import java.util.*;

/**
 * 表示一个磁盘分配方案的个体
 */
public class Individual {
    // 从以磁盘为中心改为以标签为中心的数据结构
    // 现在每个标签都有自己的分配列表，表示它在不同磁盘上的分布
    private final List<List<DiskAllocation>> tags;
    private double fitness = Double.POSITIVE_INFINITY;

    /**
     * 创建一个空的分配方案
     */
    public Individual() {
        tags = new ArrayList<>(GeneticParameters.TAGS.length);
        for (int i = 0; i < GeneticParameters.TAGS.length; i++) {
            tags.add(new ArrayList<>());
        }
    }

    /**
     * 获取某个标签在各个磁盘上的分配列表
     * 
     * @param tagId 标签索引
     * @return 磁盘分配列表
     */
    public List<DiskAllocation> getTag(int tagId) {
        return tags.get(tagId);
    }

    /**
     * 获取适应度
     * 
     * @return 适应度值
     */
    public double getFitness() {
        return fitness;
    }

    /**
     * 设置适应度
     * 
     * @param fitness 适应度值
     */
    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    /**
     * 创建当前个体的一个深拷贝
     * 
     * @return 个体副本
     */
    public Individual copy() {
        Individual copy = new Individual();

        for (int i = 0; i < tags.size(); i++) {
            List<DiskAllocation> tagCopy = new ArrayList<>();
            for (DiskAllocation disk : tags.get(i)) {
                tagCopy.add(disk.copy());
            }
            copy.tags.set(i, tagCopy);
        }

        copy.fitness = this.fitness;
        return copy;
    }

    /**
     * 添加标签到指定磁盘
     * 
     * @param tagId      标签ID
     * @param diskIdx    磁盘索引
     * @param proportion 分配比例
     */
    public void addTagToDisk(int tagId, int diskIdx, double proportion) {
        tags.get(tagId).add(new DiskAllocation(diskIdx, proportion));
    }

    /**
     * 获取各个磁盘上的标签分配
     * 重建原先的磁盘中心视图用于兼容
     * 
     * @return 磁盘到标签的映射
     */
    public Map<Integer, List<TagDiskAllocation>> getTagAllocations() {
        Map<Integer, List<TagDiskAllocation>> result = new HashMap<>();

        // 为每个标签创建结果条目
        for (int tagId = 0; tagId < tags.size(); tagId++) {
            result.put(tagId, new ArrayList<>());

            // 将标签在各个磁盘上的分配转换为TagDiskAllocation对象
            for (DiskAllocation disk : tags.get(tagId)) {
                int diskIdx = disk.getDiskIdx();
                double proportion = disk.getProportion();
                result.get(tagId).add(new TagDiskAllocation(diskIdx, proportion));
            }
        }

        return result;
    }

    /**
     * 获取磁盘上的标签分配
     * 
     * @return 每个磁盘上的标签分配
     */
    public Map<Integer, List<TagAllocation>> getDiskMap() {
        Map<Integer, List<TagAllocation>> diskMap = new HashMap<>();

        // 初始化磁盘映射
        for (int diskIdx = 0; diskIdx < GeneticParameters.NUM_DISKS; diskIdx++) {
            diskMap.put(diskIdx, new ArrayList<>());
        }

        // 将标签分配添加到相应的磁盘列表
        for (int tagId = 0; tagId < tags.size(); tagId++) {
            for (DiskAllocation disk : tags.get(tagId)) {
                int diskIdx = disk.getDiskIdx();
                double proportion = disk.getProportion();
                diskMap.get(diskIdx).add(new TagAllocation(tagId, proportion));
            }
        }

        return diskMap;
    }

    /**
     * 获取特定磁盘上的标签分配
     * 
     * @param diskIdx 磁盘索引
     * @return 该磁盘上的标签分配
     */
    public List<TagAllocation> getDisk(int diskIdx) {
        List<TagAllocation> diskTags = new ArrayList<>();

        for (int tagId = 0; tagId < tags.size(); tagId++) {
            for (DiskAllocation disk : tags.get(tagId)) {
                if (disk.getDiskIdx() == diskIdx) {
                    diskTags.add(new TagAllocation(tagId, disk.getProportion()));
                }
            }
        }

        return diskTags;
    }

    /**
     * 归一化所有标签的比例，使每个标签的总分配比例为1
     */
    public void normalizeTagProportions() {
        for (int tagId = 0; tagId < tags.size(); tagId++) {
            List<DiskAllocation> allocations = tags.get(tagId);

            // 计算总比例
            double totalProportion = allocations.stream()
                    .mapToDouble(DiskAllocation::getProportion)
                    .sum();

            if (Math.abs(totalProportion - 1.0) > 0.001) {
                // 重新归一化
                for (DiskAllocation allocation : allocations) {
                    double normalizedProportion = allocation.getProportion() / totalProportion;
                    allocation.setProportion(normalizedProportion);
                }
            }
        }
    }

    /**
     * 内部类，表示一个标签在特定磁盘上的分配
     * 保留以兼容现有代码
     */
    public static class TagDiskAllocation {
        private final int diskIdx;
        private final double proportion;

        public TagDiskAllocation(int diskIdx, double proportion) {
            this.diskIdx = diskIdx;
            this.proportion = proportion;
        }

        public int getDiskIdx() {
            return diskIdx;
        }

        public double getProportion() {
            return proportion;
        }
    }

    /**
     * 内部类，表示一个标签在特定磁盘上的分配
     */
    public static class DiskAllocation {
        private final int diskIdx;
        private double proportion;

        public DiskAllocation(int diskIdx, double proportion) {
            this.diskIdx = diskIdx;
            this.proportion = proportion;
        }

        public int getDiskIdx() {
            return diskIdx;
        }

        public double getProportion() {
            return proportion;
        }

        public void setProportion(double proportion) {
            this.proportion = proportion;
        }

        public DiskAllocation copy() {
            return new DiskAllocation(diskIdx, proportion);
        }

        @Override
        public String toString() {
            return String.format("Disk %d (%.2f%%)", diskIdx, proportion * 100);
        }
    }
}