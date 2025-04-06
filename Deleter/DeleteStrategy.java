package Deleter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import Info.Info;
import Info.model.ReadTask;
import Info.model.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public interface DeleteStrategy {
    ModuleLogger log = LoggerFactory.getLogger("Deleter");

    /**
     * 输出该对象当前所有还没完成的读取请求，这些请求将被直接取消
     * 
     * @param deleteCommandIns 删除命令输入，属性`objId`，要删除的对象ID
     * @return 删除命令输出，属性`readCommandId`，要取消的读命令ID
     */
    public ArrayList<DeleteCommandOut> delete(ArrayList<DeleteCommandIn> deleteCommandIns);

    /**
     * 查找要被终止的读任务
     */
    public default Set<ReadTask> findReadTaskToBeTerminated(int obj_id) {
        UserObject obj = Info.objMap.get(obj_id);
        Set<ReadTask> tasks_awaiting_deletion = new HashSet<>();
        // log.debug("查找要被终止的读任务，对象ID为: " + obj_id);
        for (ReadTask task : obj.readTasks) {
            log.debug("读任务ID为: " + task.taskId);
            tasks_awaiting_deletion.add(task);
        }

        return tasks_awaiting_deletion;
    }
}
