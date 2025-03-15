package IO.model;

import java.util.HashSet;
import java.util.Set;

/**
 * 删除命令输出-选手
 */
public class DeleteCommandOut {
    public Set<Integer> readCommandIds; // 要取消的读命令ID

    public DeleteCommandOut() {
        this.readCommandIds = new HashSet<>();
    }

    public DeleteCommandOut(Set<Integer> readCommandIds) {
        this.readCommandIds = readCommandIds;
    }
}