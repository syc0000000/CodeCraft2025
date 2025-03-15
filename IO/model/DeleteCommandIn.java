package IO.model;

import java.util.List;

/**
 * 删除命令输入-判题器
 */
public class DeleteCommandIn {
    public List<Integer> objIds; // 要删除的对象ID

    public DeleteCommandIn(List<Integer> objIds) {
        this.objIds = objIds;
    }
}