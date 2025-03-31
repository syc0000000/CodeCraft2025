package Writer;

import java.util.ArrayList;
import IO.model.DiskUnit;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.model.UnitData;
import Info.Info;
import Info.model.DiskSpace;
import Info.model.LocalDisk;
import Info.model.Replica;
import Info.model.Tag;
import Info.model.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * 基于标签和中间点策略的写入策略实现类
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
 * <li>{@link LocalDisk#logicalRWEnd} - 读写区域的逻辑结束位置</li>
 * <li>{@link LocalDisk#logicalBackStart} - 备份区域的逻辑起始位置</li>
 * <li>{@link LocalDisk#RWEnd} - 读写区域的当前结束位置</li>
 * </ul>
 *
 * 标签相关结构:
 * <ul>
 * <li>{@link Tag#middleList} - 每个磁盘上标签对应的中间点位置列表</li>
 * <li>{@link Tag#sizeList} - 每个磁盘上标签已使用的空间大小列表</li>
 * </ul>
 *
 * 与DefaultWriteStrategy的主要区别:
 * <ul>
 * <li>根据标签信息选择合适的磁盘进行写入</li>
 * <li>读写副本优先写入靠近标签中间点的位置</li>
 * <li>备份副本从磁盘末尾开始写入</li>
 * <li>维护标签相关的使用统计信息</li>
 * <li>严格区分读写区域和备份区域</li>
 * </ul>
 */
public class TagWriterStrategy extends DefaultWriteStrategy {
    private final ModuleLogger log = LoggerFactory.getLogger("Writer");

    @Override
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        ArrayList<WriteCommandOut> writeCommandOuts = new ArrayList<>();
        for (WriteCommandIn writeCommandIn : writeCommandIns) {
            WriteCommandOut writeCommandOut = new WriteCommandOut();
            writeCommandOut.objId = writeCommandIn.objId;
            UserObject obj = new UserObject(writeCommandIn.objId, writeCommandIn.size, writeCommandIn.tag);
            Info.objMap.put(writeCommandIn.objId, obj);
            // Get disks based on tag information
            Tag tag = Info.tags.get(writeCommandIn.tag - 1);
            ArrayList<LocalDisk> disks = selectDiskByTag(tag.tagId, obj.objSize);

            if (disks == null || disks.size() < 3) {
                log.error("无法为对象" + writeCommandIn.objId + "找到足够的磁盘");
                continue;
            }

            // rw disk
            log.debug("=== 开始为对象" + writeCommandIn.objId + "的rw replica选择磁盘 ===");
            LocalDisk rwDisk = disks.get(0);
            DiskSpace space = rwDisk.getSpaceNearMiddle(obj.objSize, tag.middleList.get(rwDisk.diskId));
            if (space == null) {
                log.error("无法为对象" + writeCommandIn.objId + "在磁盘" + rwDisk.diskId + "找到读写空间");
                // 输出整个磁盘unitData
                StringBuilder sb = new StringBuilder();
                for (UnitData unit : rwDisk.unitData) {
                    sb.append(unit.objId).append(" ");
                }
                log.error("磁盘" + rwDisk.diskId + "的unitData: " + sb.toString());
                throw new RuntimeException(
                        "无法为对象" + writeCommandIn.objId + "在磁盘" + rwDisk.diskId + "找到读写空间");
            }
            if (space != null) {
                ArrayList<Integer> unitIdList = new ArrayList<>();
                for (int j = 0; j < space.size; j++) {
                    unitIdList.add(space.start + j);
                }
                // 分配空间
                Replica replica = new Replica(writeCommandIn.objId, 0, rwDisk.diskId, unitIdList);
                addReplicaToObj(obj, replica);
                saveReplicaToDisk(rwDisk, replica);
                rwDisk.rwSizeLeft -= obj.objSize;
                // 维护RWEnd
                rwDisk.RWEnd = Math.min(rwDisk.logicalRWEnd, Math.max(rwDisk.RWEnd, space.end));
                // 维护sizeList
                tag.sizeList.set(rwDisk.diskId, tag.sizeList.get(rwDisk.diskId) + obj.objSize);
                log.debug("成功写入副本0到磁盘" + rwDisk.diskId);
                writeCommandOut.copy1 = new DiskUnit(rwDisk.diskId, unitIdList);
            }

            // 两个备份磁盘
            for (int i = 1; i < disks.size(); i++) {
                log.debug("=== 开始为对象" + writeCommandIn.objId + "的backup replica" + i + "选择磁盘 ===");
                LocalDisk backupDisk = disks.get(i);
                ArrayList<Integer> unitIdList = getFreeUnitFromEnd(backupDisk, obj.objSize);

                if (unitIdList == null) {
                    log.error("无法为对象" + writeCommandIn.objId + "在磁盘" + backupDisk.diskId + "上找到备份空间");
                    throw new RuntimeException(
                            "无法为对象" + writeCommandIn.objId + "在磁盘" + backupDisk.diskId + "上找到备份空间");
                }

                Replica replica = new Replica(writeCommandIn.objId, i, backupDisk.diskId, unitIdList);
                addReplicaToObj(obj, replica);
                for (int id : unitIdList) {
                    log.debug("disk中unitid原先的objId: " + backupDisk.unitData.get(id).objId);
                }
                saveReplicaToDisk(backupDisk, replica); // BUG 疑似这里设置ObjId没生效
                for (int id : unitIdList) {
                    log.debug("disk中unitid更新后的objId: : " + backupDisk.unitData.get(id).objId);
                }
                backupDisk.backSizeLeft -= obj.objSize;

                if (i == 1) {
                    writeCommandOut.copy2 = new DiskUnit(backupDisk.diskId, unitIdList);
                } else {
                    writeCommandOut.copy3 = new DiskUnit(backupDisk.diskId, unitIdList);
                }
            }

            writeCommandOuts.add(writeCommandOut);
        }
        return writeCommandOuts;
    }

    /**
     * 负责挑选unit来存放对象，不负责信息的更新，这样如果没有挑选到unit，不必回退信息。
     * 
     * @param disk
     * @param objSize
     * @param objId
     * @return ArrayList<Integer> unitIdList | null
     */
    private ArrayList<Integer> getFreeUnitFromEnd(LocalDisk disk, int objSize) {
        ArrayList<Integer> unitIdList = new ArrayList<>(objSize);
        int count = 0;

        // Iterate from the end of disk to the logical backup start
        for (int i = disk.unitNum - 1; i >= disk.logicalBackStart && count < objSize; i--) {
            // pick the free unit
            if (disk.unitData.get(i).objId == -1) {
                log.debug("Found free unit: disk=" + disk.diskId + ", unit=" + i
                        + ", current objId=" + disk.unitData.get(i).objId);
                unitIdList.add(i);
                count++;
            } else {
                // Log when we encounter allocated units
                log.debug("Skipping allocated unit: disk=" + disk.diskId + ", unit=" + i
                        + ", used by objId=" + disk.unitData.get(i).objId);
            }
        }

        // Return null if we couldn't find enough free units
        if (count < objSize) {
            return null;
        }

        return unitIdList;
    }

    /**
     * 基于给定的标签ID和对象大小选择磁盘。 选择一个与标签关联的读写磁盘，以及两个具有最大可用空间的备份磁盘。 <del>1.
     * 函数内部更换tag对应的disk的sizeList</del>
     *
     * @param tagId   标签的ID。
     * @param objSize 要写入的对象的大小。
     * @return 一个包含所选 LocalDisk 对象的 ArrayList。 列表中的第一个磁盘是读写磁盘，后跟两个备份磁盘（如果可用）。
     */
    private ArrayList<LocalDisk> selectDiskByTag(int tagId, int objSize) {
        ArrayList<LocalDisk> candidateDisks = new ArrayList<>(3);

        // select disks based on tag information
        Tag tag = Info.tags.get(tagId);
        LocalDisk rwDisk = getMaxSpaceDisk(tag);

        // 选两个磁盘放对象的backup replica，优先选择sizeLeft最大的2个磁盘
        LocalDisk backupDisk1 = null, backupDisk2 = null;
        int size1 = Integer.MIN_VALUE, size2 = Integer.MIN_VALUE;
        for (LocalDisk disk : Info.localDiskTbl) {
            if (disk.diskId == rwDisk.diskId) {
                continue;
            }
            int sizeLeft = disk.backSizeLeft;
            if (sizeLeft > size1) {
                size2 = size1;
                size1 = sizeLeft;
                backupDisk2 = backupDisk1;
                backupDisk1 = disk;
            } else if (sizeLeft > size2) {
                size2 = sizeLeft;
                backupDisk2 = disk;
            }
        }

        if (rwDisk == null || backupDisk1 == null || backupDisk2 == null) {
            log.error("没有找到可用的磁盘");
            throw new RuntimeException("没有找到可用的磁盘");
        }
        candidateDisks.add(rwDisk);
        candidateDisks.add(backupDisk1);
        candidateDisks.add(backupDisk2);
        log.debug("选择磁盘: rwDisk=" + rwDisk.diskId + ", backupDisk1="
                + (backupDisk1 == null ? "null" : backupDisk1.diskId) + ", backupDisk2="
                + (backupDisk2 == null ? "null" : backupDisk2.diskId));
        return candidateDisks;
    }

    /**
     * 根据tag获取读写区剩余最大空间的磁盘
     * 
     * @param tag
     * @return 剩余空间最大的磁盘
     */
    private LocalDisk getMaxSpaceDisk(Tag tag) {
        // 找到有tag区域的所有磁盘
        ArrayList<LocalDisk> candidateDisks = new ArrayList<>();
        for (int i = 0; i < tag.middleList.size(); i++) {
            if (tag.middleList.get(i) != 0) {
                candidateDisks.add(Info.localDiskTbl.get(i));
            }
        }
        // 找到剩余空间最大的磁盘
        LocalDisk maxSpaceDisk = null;
        int maxSpace = Integer.MIN_VALUE;
        for (LocalDisk disk : candidateDisks) {
            if (disk.rwSizeLeft > maxSpace) {
                maxSpace = disk.rwSizeLeft;
                maxSpaceDisk = disk;
            }
        }
        return maxSpaceDisk;
    }
}
