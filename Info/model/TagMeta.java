package Info.model;

public class TagMeta {
    public int tagId;
    public int left;
    public int right;
    public int rightNow;

    public TagMeta(int tagId, int left, int right, int rightNow) {
        this.tagId = tagId;
        this.left = left;
        this.right = right;
        this.rightNow = rightNow;
    }
}
