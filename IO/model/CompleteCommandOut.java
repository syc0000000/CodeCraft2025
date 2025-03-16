package IO.model;

import java.util.Objects;

/**
 * 读成功命令-选手
 * 在某对象读取完成后，输出这个对象对应的读取命令id
 */
public class CompleteCommandOut {
    public int commandId; // 命令ID

    public CompleteCommandOut() {
    }

    public CompleteCommandOut(int commandId) {
        this.commandId = commandId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CompleteCommandOut that = (CompleteCommandOut) obj;
        return commandId == that.commandId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandId);
    }
}