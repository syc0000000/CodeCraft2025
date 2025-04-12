package Deleter;

import java.util.ArrayList;
import java.util.Set;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import Info.Info;
import Info.model.DiskSpace;
import Info.model.LocalDisk;
import Info.model.ReadTask;
import Info.model.Replica;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * 默认的删除策略实现类
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
 * <li>{@link LocalDisk#freespaceBySize} - 跟踪每个磁盘上的可用空间</li>
 * </ul>
 *
 * 主要功能:
 * <ul>
 * <li>清理对象占用的磁盘空间</li>
 * <li>维护磁盘的空闲空间信息</li>
 * <li>终止对象相关的读任务</li>
 * <li>更新磁盘单元的任务状态</li>
 * </ul>
 */
public class DefaultDeleteStrategy implements DeleteStrategy {
    ModuleLogger log = LoggerFactory.getLogger("Deleter");

    @Override
    public ArrayList<DeleteCommandOut> delete(ArrayList<DeleteCommandIn> deleteCommandIns) {
        ArrayList<DeleteCommandOut> deleteCommandOuts = new ArrayList<>();

        for (DeleteCommandIn deleteCommandIn : deleteCommandIns) {
            int obj_id = deleteCommandIn.objId;
            maintainLocalDiskInfo(obj_id);
            Set<ReadTask> tasks_awaiting_deletion = findReadTaskToBeTerminated(obj_id);

            // 维护unit单元是否有任务的属性

            for (int j = 0; j < 3; j++) {
                // 三个副本
                Replica replica = Info.objMap.get(obj_id).replicas.get(j);
                // 每一个副本的对应unit都置为false
                for (int i = 0; i < Info.objMap.get(obj_id).objSize; i++) {
                    Info.localDiskTbl.get(replica.diskId).unitData
                            .get(replica.unitIdList.get(i)).isInTask = false;
                }
            }

            Info.objMap.remove(obj_id);
            for (ReadTask task : tasks_awaiting_deletion) {
                deleteCommandOuts.add(new DeleteCommandOut(task.taskId));
            }
        }
        return deleteCommandOuts;
    }

    /**
     * 负责释放指定空间 维护freeSpaceBySize 时间复杂度：O(3 * 5 * n) = O(n)
     * 
     * @param obj_id
     * @return
     */
    private void maintainLocalDiskInfo(int obj_id) {
        ArrayList<Replica> replicas = Info.objMap.get(obj_id).replicas;
        // log.debug("准备释放 Obj_ID = " + obj_id + " 所占用的空间");
        for (Replica replica : replicas) { // 循环3次
            // log.debug("开始释放副本所占用的空间，副本信息：" + replica);
            int disk_id = replica.diskId;
            LocalDisk disk = Info.localDiskTbl.get(disk_id);
            DiskSpace space = disk.unitData.get(replica.unitIdList.get(0)).space;
            // log.debug("释放空间: " + space);
            disk.releaseSpace(space);
        }
    }
}
