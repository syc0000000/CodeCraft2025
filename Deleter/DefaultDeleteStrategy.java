package Deleter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.Replica;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class DefaultDeleteStrategy implements DeleteStrategy {
    ModuleLogger log = LoggerFactory.getLogger("Deleter");

    @Override
    public ArrayList<DeleteCommandOut> delete(ArrayList<DeleteCommandIn> deleteCommandIns) {
        ArrayList<DeleteCommandOut> deleteCommandOuts = new ArrayList<>();

        for (DeleteCommandIn deleteCommandIn : deleteCommandIns) {
            int obj_id = deleteCommandIn.objId;
            maintainLocalDiskInfo(obj_id);
            Set<Integer> tasks_awaiting_deletion = findReadTaskToBeTerminated(obj_id);
            for (int task_id : tasks_awaiting_deletion) {
                deleteCommandOuts.add(new DeleteCommandOut(task_id));
            }
        }
        return deleteCommandOuts;
    }

    /**
     * 负责释放指定空间 维护freeSpaceBySize
     * 时间复杂度：O(3 * 5 * n) = O(n)
     * 
     * @param obj_id
     * @return
     */
    private void maintainLocalDiskInfo(int obj_id) {
        ArrayList<Replica> replicas = Info.objMap.get(obj_id).replicas;
        log.debug("准备释放 Obj_ID = " + obj_id + " 所占用的空间");
        for (Replica replica : replicas) { // 循环3次
            log.debug("开始释放副本所占用的空间，副本信息：" + replica);
            int disk_id = replica.diskId;
            ArrayList<Integer> unit_ids = replica.unitIdList;
            LocalDisk localDisk = Info.localDiskTbl.get(disk_id);
            for (int id : unit_ids) { // 最多循环5次
                log.debug("释放空间: " + localDisk.getSpaceForUnit(id));
                localDisk.releaseSpace(localDisk.getSpaceForUnit(id));
            }
        }
    }

    /**
     * 查找要被终止的读任务
     */
    public Set<Integer> findReadTaskToBeTerminated(int obj_id) {
        Set<Integer> tasks_awaiting_deletion = Info.objTaskMap.get(obj_id);
        // 在readTaskTbl中删除该任务
        if (tasks_awaiting_deletion != null) {
            for (int task_id : tasks_awaiting_deletion) {
                log.debug("取消任务, ID = " + task_id);
                Info.readTaskTbl.remove(task_id);
            }
        } else {
            log.debug("没有找到要被终止的读任务");
            return new HashSet<>();
        }
        // 在objTaskMap中删除该对象
        Info.objTaskMap.remove(obj_id);

        // 删除该对象过期的任务
        // TODO: Your task here

        return tasks_awaiting_deletion;
    }
}
