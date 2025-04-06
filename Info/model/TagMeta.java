package Info.model;

public class TagMeta {
    public int tagId;
    public int left;
    public int right;
    public int rightNow;
    public int sizeNow;

    public TagMeta(int tagId, int left, int right, int rightNow) {
        this.tagId = tagId;
        this.left = left;
        this.right = right;
        this.rightNow = rightNow;
    }

    public TagMeta(int tagId, int left, int right, int rightNow, int sizeNow) {
        this.tagId = tagId;
        this.left = left;
        this.right = right;
        this.rightNow = rightNow;
        this.sizeNow = sizeNow;
    }

    @Override
    public String toString() {
        return "TagMeta [tagId=" + tagId + ", left=" + left + ", right=" + right + ", rightNow=" + rightNow + "]";
    }
}
