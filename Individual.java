import java.util.*;
import java.util.stream.Collectors;

/**
 * 表示一个磁盘分配方案的个体
 */
public class Individual {
    private final List<List<TagAllocation>> disks;
    private double fitness = Double.POSITIVE_INFINITY;

    /**
     * 创建一个空的分配方案
     */
    public Individual() {
        disks = new ArrayList<>(GeneticDiskDistribution.NUM_DISKS);
        for (int i = 0; i < GeneticDiskDistribution.NUM_DISKS; i++) {
            disks.add(new ArrayList<>());
        }
    }

    /**
     * 获取某个磁盘上的标签分配列表
     * 
     * @param diskIdx 磁盘索引
     * @return 标签分配列表
     */
    public List<TagAllocation> getDisk(int diskIdx) {
        return disks.get(diskIdx);
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

        for (int i = 0; i < disks.size(); i++) {
            List<TagAllocation> diskCopy = new ArrayList<>();
            for (TagAllocation tag : disks.get(i)) {
                diskCopy.add(tag.copy());
            }
            copy.disks.set(i, diskCopy);
        }

        copy.fitness = this.fitness;
        return copy;
    }

    /**
     * 添加标签到指定磁盘
     * 
     * @param diskIdx    磁盘索引
     * @param tagId      标签ID
     * @param proportion 分配比例
     */
    public void addTagToDisk(int diskIdx, int tagId, double proportion) {
        disks.get(diskIdx).add(new TagAllocation(tagId, proportion));
    }

    /**
     * 获取标签在所有磁盘上的分配
     * 
     * @return 映射，键为标签ID，值为该标签在各个磁盘上的分配信息
     */
    public Map<Integer, List<TagDiskAllocation>> getTagAllocations() {
        Map<Integer, List<TagDiskAllocation>> result = new HashMap<>();

        for (int diskIdx = 0; diskIdx < disks.size(); diskIdx++) {
            for (TagAllocation tag : disks.get(diskIdx)) {
                int tagId = tag.getTagId();
                double proportion = tag.getProportion();

                if (!result.containsKey(tagId)) {
                    result.put(tagId, new ArrayList<>());
                }

                result.get(tagId).add(new TagDiskAllocation(diskIdx, proportion));
            }
        }

        return result;
    }

    /**
     * 归一化所有标签的比例，使每个标签的总分配比例为1
     */
    public void normalizeTagProportions() {
        Map<Integer, List<TagDiskAllocation>> tagAllocations = getTagAllocations();

        for (Map.Entry<Integer, List<TagDiskAllocation>> entry : tagAllocations.entrySet()) {
            int tagId = entry.getKey();
            List<TagDiskAllocation> allocations = entry.getValue();

            // 计算总比例
            double totalProportion = allocations.stream()
                    .mapToDouble(TagDiskAllocation::getProportion)
                    .sum();

            if (Math.abs(totalProportion - 1.0) > 0.001) {
                // 重新归一化
                for (TagDiskAllocation allocation : allocations) {
                    int diskIdx = allocation.getDiskIdx();
                    double normalizedProportion = allocation.getProportion() / totalProportion;

                    // 更新磁盘上的标签比例
                    for (TagAllocation tag : disks.get(diskIdx)) {
                        if (tag.getTagId() == tagId) {
                            tag.setProportion(normalizedProportion);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * 内部类，表示一个标签在特定磁盘上的分配
     */
    private static class TagDiskAllocation {
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
}