package Deleter;

import java.util.ArrayList;
import java.util.Set;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;


public class DefaultDeleteStrategy implements DeleteStrategy {
    @Override
    public ArrayList<DeleteCommandOut> delete(ArrayList<DeleteCommandIn> deleteCommandIns) {
        ArrayList<DeleteCommandOut> deleteCommandOuts = new ArrayList<>();
        return deleteCommandOuts;
    }


    /**
     * 查找要被终止的读任务
     */
    public void findReadTaskToBeTerminated(int obj_id) {
        Set<Integer> affected_tasks = Info.Info.objTaskMap.get(obj_id);
        for (int task_id : affected_tasks) {
            ReadTask read_task = Info.Info.readTaskTbl.get(task_id);
            if (read_task != null) {
                read_task.terminate();
            }
        }
    }
}
