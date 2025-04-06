package Deleter;

import java.util.ArrayList;
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

/**
 * 基于标签信息的删除策略实现类
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
 * 与DefaultDeleteStrategy的主要区别:
 * <ul>
 * <li>区分读写副本和备份副本的删除处理</li>
 * <li>维护标签使用空间统计信息</li>
 * <li>更新读写区域边界位置</li>
 * <li>不使用freespaceBySize结构,直接操作单元数组</li>
 * </ul>
 */
public class NewTagDeleteStrategy implements DeleteStrategy {
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
}
