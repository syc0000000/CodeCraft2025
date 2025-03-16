package Deleter;

import java.util.ArrayList;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;

public interface DeleteStrategy {
    /**
     * 输出该对象当前所有还没完成的读取请求，这些请求将被直接取消
     * 
     * @param deleteCommandIns 删除命令输入，属性`objId`，要删除的对象ID
     * @return 删除命令输出，属性`readCommandId`，要取消的读命令ID
     */
    public ArrayList<DeleteCommandOut> delete(ArrayList<DeleteCommandIn> deleteCommandIns);
}
