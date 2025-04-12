package IO.model;

/**
 * 删除命令输出-选手
 */
public class DeleteCommandOut {
    public Integer readCommandId; // 要取消的读命令ID

    public DeleteCommandOut() {
        this.readCommandId = null;
    }

    public DeleteCommandOut(Integer readCommandId) {
        this.readCommandId = readCommandId;
    }
}