package Deleter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.DiskSpace;
import Info.Info.ReadTask;
import Info.Info.Replica;
import Info.Info.UserObject;
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
            Info.objMap.remove(obj_id);

            for (int task_id : tasks_awaiting_deletion) {
                deleteCommandOuts.add(new DeleteCommandOut(task_id));
            }
        }
        return deleteCommandOuts;
    }

    /**
     * 负责释放指定空间 维护的信息,unitData
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
            LocalDisk localDisk = Info.localDiskTbl.get(disk_id);
            ArrayList<Integer> unit_ids = replica.unitIdList;
            for (int unit_id : unit_ids) {
                UnitData unitData = Info.localDiskTbl.get(disk_id).unitData.get(unit_id);
                if (unitData == null) {
                    log.debug("unitData = null, disk_id = " + disk_id + ", unit_id = " + unit_id);
                    continue;
                }
                unitData.obj_id = -1;
                unitData.blockId = -1;
                if (unit_id == localDisk.rwEnd) {
                    localDisk.rwEnd -= 1;
                }
            }
        }
    }

    /**
     * 查找要被终止的读任务
     */
    public Set<Integer> findReadTaskToBeTerminated(int obj_id) {
        UserObject obj = Info.objMap.get(obj_id);
        Set<Integer> tasks_awaiting_deletion = new HashSet<>();

        if (obj.readTasks == null) {
            log.debug("当前对象没有正在进行的读任务");
        }
        for (ReadTask task : obj.readTasks) {
            log.debug("终止进行中的读任务, ID = " + task.taskId);
            tasks_awaiting_deletion.add(task.taskId);
        }

        if (obj.readTasks == null) {
            log.debug("当前对象已经超时的读任务");
        }

        for (int task_id : obj.timeoutTasks) {
            log.debug("终止已超时的读任务, ID = " + task_id);
            tasks_awaiting_deletion.add(task_id);
        }
        return tasks_awaiting_deletion;
    }
}
