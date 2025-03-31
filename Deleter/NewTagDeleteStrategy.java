package Deleter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import Info.Info;
import Info.model.LocalDisk;
import Info.model.ReadTask;
import Info.model.Replica;
import Info.model.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class NewTagDeleteStrategy implements DeleteStrategy {
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
        // free rw replica
        Replica replica = replicas.get(0);
        log.debug("开始释放RW副本所占用的空间，副本信息：" + replica);
        int disk_id = replica.diskId;
        ArrayList<Integer> unit_ids = replica.unitIdList;
        LocalDisk rwDisk = Info.localDiskTbl.get(disk_id);
        for (int unit_id : unit_ids) {
            // 更新rwend
            if (unit_id == rwDisk.RWEnd) {
                while (rwDisk.RWEnd > 0 && (rwDisk.unitData.get(rwDisk.RWEnd - 1).objId == -1)) {
                    rwDisk.RWEnd--;
                }
            }
            rwDisk.unitData.get(unit_id).objId = -1;
            rwDisk.unitData.get(unit_id).blockId = -1;
            rwDisk.unitData.get(unit_id).isInTask = false;
            rwDisk.rwSizeLeft += 1;
            // 维护sizeList
            UserObject obj = Info.objMap.get(obj_id);
            Info.tags.get(obj.objTag - 1).sizeList.set(rwDisk.diskId,
                    Info.tags.get(obj.objTag - 1).sizeList.get(rwDisk.diskId) - 1);
        }

        // free backup replica
        for (int i = 1; i < replicas.size(); i++) {
            replica = replicas.get(i);
            log.debug("开始释放备份副本所占用的空间，副本信息：" + replica);
            disk_id = replica.diskId;
            unit_ids = replica.unitIdList;
            LocalDisk backupDisk = Info.localDiskTbl.get(disk_id);

            for (int unit_id : unit_ids) {
                backupDisk.unitData.get(unit_id).objId = -1;
                backupDisk.unitData.get(unit_id).blockId = -1;
                backupDisk.backSizeLeft += 1;
                backupDisk.unitData.get(unit_id).isInTask = false;
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
