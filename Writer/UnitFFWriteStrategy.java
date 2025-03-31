package Writer;

import java.util.ArrayList;

import IO.model.DiskUnit;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.Info;
import Info.model.LocalDisk;
import Info.model.Replica;
import Info.model.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * 基于单元级别优先适配的写入策略实现类
 *
 * 数据结构:
 *
 * 依赖的全局存储结构:
 * <ul>
 * <li>{@link Info#objMap} - 用户对象的全局存储表,以对象ID为索引</li>
 * <li>{@link Info#localDiskTbl} - 存储所有可用磁盘的信息表</li>
 * <li>{@link Info#MAX_RW_END} - 读写区域的最大结束位置</li>
 * </ul>
 *
 * 磁盘相关结构:
 * <ul>
 * <li>{@link LocalDisk#unitData} - 存储每个磁盘的单元级存储信息</li>
 * <li>{@link LocalDisk#sizeLeft} - 磁盘剩余总空间大小</li>
 * <li>{@link LocalDisk#RWEnd} - 读写区域的当前结束位置</li>
 * </ul>
 *
 * 与DefaultWriteStrategy的主要区别:
 * <ul>
 * <li>采用单元级别的优先适配算法选择存储位置</li>
 * <li>基于磁盘剩余空间大小选择目标磁盘</li>
 * <li>读写区域采用从前向后扫描的方式寻找空闲单元</li>
 * <li>备份区域从磁盘末尾开始查找连续空闲单元</li>
 * <li>不使用freespaceBySize结构,直接操作单元数组</li>
 * </ul>
 */
public class UnitFFWriteStrategy extends DefaultWriteStrategy {
    private final ModuleLogger log = LoggerFactory.getLogger("Writer");

    @Override
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        ArrayList<WriteCommandOut> writeCommandOuts = new ArrayList<>();
        for (WriteCommandIn writeCommandIn : writeCommandIns) {
            WriteCommandOut writeCommandOut = new WriteCommandOut();
            writeCommandOut.objId = writeCommandIn.objId;
            UserObject obj = new UserObject(writeCommandIn.objId, writeCommandIn.size, writeCommandIn.tag);
            Info.objMap.put(writeCommandIn.objId, obj);
            ArrayList<LocalDisk> disks = selectDiskBySizeLeft();

            // 处理RW磁盘
            if (!disks.isEmpty()) {
                LocalDisk rwDisk = disks.get(0);
                ArrayList<Integer> unitIdList = rwDisk.getFreeUnitsBySize(obj.objSize, obj.objId);

                if (!unitIdList.isEmpty()) {
                    // 分配空间
                    Replica replica = new Replica(writeCommandIn.objId, 0, rwDisk.diskId, unitIdList);
                    addReplicaToObj(obj, replica);
                    saveReplicaToDisk(rwDisk, replica);

                    // 维护RWEnd - 找出最大的unitId
                    int maxUnitId = Integer.MIN_VALUE;
                    for (Integer unitId : unitIdList) {
                        maxUnitId = Math.max(maxUnitId, unitId);
                    }
                    rwDisk.RWEnd = Math.min(Info.MAX_RW_END, Math.max(rwDisk.RWEnd, maxUnitId + 1));

                    log.debug("成功写入副本0到磁盘" + rwDisk.diskId);
                    writeCommandOut.copy1 = new DiskUnit(rwDisk.diskId, unitIdList);
                }
            }

            // 处理Backup磁盘
            for (int i = 1; i < disks.size() && i <= 2; i++) {
                LocalDisk backupDisk = disks.get(i);
                ArrayList<Integer> unitIdList = backupDisk.getFreeUnitBySizeFromEndWithRWEndLimit(obj.objSize,
                        obj.objId);

                if (!unitIdList.isEmpty()) {
                    // 分配空间
                    Replica replica = new Replica(writeCommandIn.objId, i, backupDisk.diskId, unitIdList);
                    addReplicaToObj(obj, replica);
                    saveReplicaToDisk(backupDisk, replica);

                    log.debug("成功写入副本" + i + "到磁盘" + backupDisk.diskId);
                    if (i == 1) {
                        writeCommandOut.copy2 = new DiskUnit(backupDisk.diskId, unitIdList);
                    } else if (i == 2) {
                        writeCommandOut.copy3 = new DiskUnit(backupDisk.diskId, unitIdList);
                    }
                }
            }
            writeCommandOuts.add(writeCommandOut);
        }
        return writeCommandOuts;
    }

    /**
     * 选择3个磁盘，[0]作为RW磁盘，[1,2]作为Backup磁盘。
     * 
     * @return 按可用空间排序的磁盘列表
     */
    private ArrayList<LocalDisk> selectDiskBySizeLeft() {
        ArrayList<LocalDisk> disks = new ArrayList<>(3);
        // 使用三个变量记录最大的三个磁盘，[0]作为RW磁盘，[1,2]作为Backup磁盘。
        // O(n)实现，虽然看起来有点丑
        LocalDisk max1 = null, max2 = null, max3 = null;
        int size1 = Integer.MIN_VALUE, size2 = Integer.MIN_VALUE, size3 = Integer.MIN_VALUE;

        // 一次遍历找出最大的三个磁盘
        for (LocalDisk disk : Info.localDiskTbl) {
            if (disk.sizeLeft > size1) {
                max3 = max2;
                size3 = size2;
                max2 = max1;
                size2 = size1;
                max1 = disk;
                size1 = disk.sizeLeft;
            } else if (disk.sizeLeft > size2) {
                max3 = max2;
                size3 = size2;
                max2 = disk;
                size2 = disk.sizeLeft;
            } else if (disk.sizeLeft > size3) {
                max3 = disk;
                size3 = disk.sizeLeft;
            }
        }

        // 按顺序添加到结果列表中
        if (max1 != null)
            disks.add(max1);
        if (max2 != null)
            disks.add(max2);
        if (max3 != null)
            disks.add(max3);

        return disks;
    }
}
