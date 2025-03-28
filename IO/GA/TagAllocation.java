package IO.GA;

/**
 * 表示一个标签在一个磁盘上的分配
 */
public class TagAllocation {
    private final int tagId;
    private double proportion;

    /**
     * 构造一个标签分配
     * 
     * @param tagId      标签ID
     * @param proportion 分配比例，0到1之间
     */
    public TagAllocation(int tagId, double proportion) {
        this.tagId = tagId;
        this.proportion = proportion;
    }

    /**
     * 获取标签ID
     * 
     * @return 标签ID
     */
    public int getTagId() {
        return tagId;
    }

    /**
     * 获取分配比例
     * 
     * @return 分配比例
     */
    public double getProportion() {
        return proportion;
    }

    /**
     * 设置分配比例
     * 
     * @param proportion 新的分配比例
     */
    public void setProportion(double proportion) {
        this.proportion = proportion;
    }

    /**
     * 创建此分配的一个副本
     * 
     * @return 副本
     */
    public TagAllocation copy() {
        return new TagAllocation(tagId, proportion);
    }

    @Override
    public String toString() {
        return String.format("Tag %d (%.2f%%)", tagId, proportion * 100);
    }
}