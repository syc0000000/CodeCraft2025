package IO.model;

/**
 * 读命令输入-判题器
 */
public class ReadCommandIn {
    public int commandId; // 命令ID
    public int objId; // 对象ID

    public ReadCommandIn() {
    }

    public ReadCommandIn(int commandId, int objId) {
        this.commandId = commandId;
        this.objId = objId;
    }
}