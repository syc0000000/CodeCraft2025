package IO.model;

/**
 * 写命令输入-判题器
 */
public class WriteCommandIn {
    public int objId; // 对象ID
    public int size; // 对象大小
    public int tag; // 对象标签

    public WriteCommandIn() {
    }

    public WriteCommandIn(int objId, int size, int tag) {
        this.objId = objId;
        this.size = size;
        this.tag = tag;
    }
}