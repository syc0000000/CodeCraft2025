package Deleter;

import java.util.ArrayList;
import java.util.Set;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import Info.Info;
import Info.model.DiskSpace;
import Info.model.DiskSpaceType;
import Info.model.LocalDisk;
import Info.model.ReadTask;
import Info.model.Replica;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * 基于标签和空间类型的删除策略实现类
 *
 * 数据结构:
 *
 * 依赖的全局存储结构:
 * <ul>
 * <li>{@link Info#objMap} - 用户对象的全局存储表,以对象ID为索引</li>
 * <li>{@link Info#localDiskTbl} - 存储所有可用磁盘的信息表</li>
 * <li>{@link Info#tags} - 存储所有标签信息的列表</li>
 * </ul>
 *
 * 磁盘相关结构:
 * <ul>
 * <li>{@link LocalDisk#unitData} - 存储每个磁盘的单元级存储信息</li>
 * <li>{@link LocalDisk#rwSizeLeft} - 磁盘剩余读写区域大小</li>
 * <li>{@link LocalDisk#backSizeLeft} - 磁盘剩余备份区域大小</li>
 * <li>{@link LocalDisk#RWEnd} - 读写区域的当前结束位置</li>
 * </ul>
 *
 * 空间管理结构:
 * <ul>
 * <li>{@link DiskSpace#type} - 空间类型(未使用/读写/备份)</li>
 * <li>{@link DiskSpace#isFree} - 空间是否空闲</li>
 * </ul>
 *
 * 与DefaultDeleteStrategy的主要区别:
 * <ul>
 * <li>维护空间类型信息</li>
 * <li>支持空间合并操作</li>
 * <li>区分读写和备份空间的释放过程</li>
 * <li>更新标签使用统计信息</li>
 * <li>对读写区域边界进行特殊处理</li>
 * </ul>
 */
public class TagDeleteStrategy implements DeleteStrategy {
    ModuleLogger log = LoggerFactory.getLogger("Deleter");

    @Override
    public ArrayList<DeleteCommandOut> delete(ArrayList<DeleteCommandIn> deleteCommandIns) {
        ArrayList<DeleteCommandOut> deleteCommandOuts = new ArrayList<>();

        for (DeleteCommandIn deleteCommandIn : deleteCommandIns) {
            int obj_id = deleteCommandIn.objId;
            maintainLocalDiskInfo(obj_id);
            Set<ReadTask> tasks_awaiting_deletion = findReadTaskToBeTerminated(obj_id);
            Info.objMap.remove(obj_id);

            for (ReadTask task : tasks_awaiting_deletion) {
                deleteCommandOuts.add(new DeleteCommandOut(task.taskId));
                Info.readTasksInRecent105Tick.get(Info.readTasksInRecent105Tick.size() - 1
                        - (Info.timestamp - task.startTime))
                        .remove(task);
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
                while (rwDisk.RWEnd > 0 && (rwDisk.unitData
                        .get(rwDisk.RWEnd - 1).space.type == DiskSpaceType.UNUSED
                        || rwDisk.unitData
                                .get(rwDisk.RWEnd - 1).space.type == DiskSpaceType.BACKUPSPACE)) {
                    rwDisk.RWEnd--;
                }
            }
            rwDisk.unitData.get(unit_id).space = space;
            rwDisk.unitData.get(unit_id).objId = -1;
            rwDisk.unitData.get(unit_id).blockId = -1;
            rwDisk.unitData.get(unit_id).isInTask = false;
            rwDisk.rwSizeLeft += 1;
            // 维护sizeList
            // UserObject obj = Info.objMap.get(obj_id);
            // Info.tags.get(obj.objTag - 1).sizeList.set(rwDisk.diskId,
            // Info.tags.get(obj.objTag - 1).sizeList.get(rwDisk.diskId) - 1);
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
        if (prevSpace != null && prevSpace.isFree && prevSpace.tagId == space.tagId) {
            // log.debug("合并前空间: " + prevSpace);
            space.setStartAndEnd(prevSpace.start, space.end);
        }
        if (nextSpace != null && nextSpace.isFree && nextSpace.tagId == space.tagId) {
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
