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
 * 基于单元级别优先适配的删除策略实现类
 *
 * 数据结构:
 *
 * 依赖的全局存储结构:
 * <ul>
 * <li>{@link Info#objMap} - 用户对象的全局存储表,以对象ID为索引</li>
 * <li>{@link Info#localDiskTbl} - 存储所有可用磁盘的信息表</li>
 * </ul>
 *
 * 磁盘相关结构:
 * <ul>
 * <li>{@link LocalDisk#unitData} - 存储每个磁盘的单元级存储信息</li>
 * <li>{@link LocalDisk#freespaceNotBySize} - 使用链表存储空闲空间信息</li>
 * <li>{@link LocalDisk#sizeLeft} - 磁盘剩余总空间大小</li>
 * <li>{@link LocalDisk#RWEnd} - 读写区域的当前结束位置</li>
 * </ul>
 *
 * 空间管理结构:
 * <ul>
 * <li>{@link DiskSpace#type} - 空间类型(未使用/读写/备份)</li>
 * </ul>
 *
 * 与DefaultDeleteStrategy的主要区别:
 * <ul>
 * <li>使用单一链表维护空闲空间</li>
 * <li>统一处理所有副本的释放</li>
 * <li>维护磁盘总剩余空间大小</li>
 * <li>对读写区域边界进行特殊处理</li>
 * <li>不区分读写和备份空间的释放过程</li>
 * </ul>
 */
public class UnitFFDeleteStrategy implements DeleteStrategy {
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
            ArrayList<Integer> unit_ids = replica.unitIdList;
            LocalDisk disk = Info.localDiskTbl.get(disk_id);

            for (int unit_id : unit_ids) {
                DiskSpace space = disk.unitData.get(unit_id).space;
                space.type = DiskSpaceType.UNUSED;
                // 更新rwend
                if (space.end == disk.RWEnd) {
                    while (disk.RWEnd > 0 && (disk.unitData
                            .get(disk.RWEnd - 1).space.type == DiskSpaceType.UNUSED
                            || disk.unitData
                                    .get(disk.RWEnd - 1).space.type == DiskSpaceType.BACKUPSPACE)) {
                        disk.RWEnd--;
                    }
                }
                disk.unitData.get(unit_id).space = space;
                disk.unitData.get(unit_id).objId = -1;
                disk.unitData.get(unit_id).blockId = -1;
                disk.freespaceNotBySize.add(space);
                disk.sizeLeft += space.size;
            }
        }
    }
}
