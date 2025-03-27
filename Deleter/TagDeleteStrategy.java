package Deleter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import Info.Info;
import Info.Info.DiskSpace;
import Info.Info.DiskSpaceType;
import Info.Info.LocalDisk;
import Info.Info.ReadTask;
import Info.Info.Replica;
import Info.Info.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class TagDeleteStrategy implements DeleteStrategy {
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
            DiskSpace space = rwDisk.unitData.get(unit_id).space;
            space.type = DiskSpaceType.UNUSED;
            // 更新rwend
            if (space.end == rwDisk.RWEnd) {
                while (rwDisk.RWEnd > 0
                        && (rwDisk.unitData.get(rwDisk.RWEnd - 1).space.type == DiskSpaceType.UNUSED
                                || rwDisk.unitData.get(
                                        rwDisk.RWEnd - 1).space.type == DiskSpaceType.BACKUPSPACE)) {
                    rwDisk.RWEnd--;
                }
            }
            rwDisk.unitData.get(unit_id).space = space;
            rwDisk.unitData.get(unit_id).objId = -1;
            rwDisk.unitData.get(unit_id).blockId = -1;
            rwDisk.unitData.get(unit_id).isInTask = false;
            rwDisk.rwSizeLeft += 1;
            releaseSpace(space);
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

    /**
     * 释放空间
     * 
     * @param space
     */
    private void releaseSpace(DiskSpace space) {
        log.debug("释放空间: " + space);
        LocalDisk disk = Info.localDiskTbl.get(space.diskId);
        int diskId = disk.diskId;
        if (space.diskId != diskId) {
            log.error("释放空间: " + space + " 不是本磁盘，异常");
            return;// 不是本磁盘，异常报错
        }

        // isFree = true
        space.isFree = true;
        space.type = DiskSpaceType.UNUSED;
        // 合并前后空间
        DiskSpace prevSpace = space.start > 0 ? disk.unitData.get(space.start - 1).space : null;
        DiskSpace nextSpace = space.end < disk.unitNum - 1 ? disk.unitData.get(space.end + 1).space : null;
        if (prevSpace != null && prevSpace.isFree) {
            // log.debug("合并前空间: " + prevSpace);
            space.setStartAndEnd(prevSpace.start, space.end);
        }
        if (nextSpace != null && nextSpace.isFree) {
            // log.debug("合并后空间: " + nextSpace);
            space.setStartAndEnd(space.start, nextSpace.end);
        }
        // 更新单元到空间的映射
        for (int i = space.start; i <= space.end; i++) {
            disk.unitData.get(i).space = space;
            disk.unitData.get(i).objId = -1;
            disk.unitData.get(i).blockId = -1;
        }
        // 更新按大小组织的集合
        log.debug("释放完成: " + space.toString());
    }
}
