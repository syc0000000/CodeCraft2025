package Writer;

import java.util.ArrayList;
import IO.model.DiskUnit;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.Replica;
import Info.Info.Tag;
import Info.Info.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class NewTagWriterStrategy extends DefaultWriteStrategy {
    private final ModuleLogger log = LoggerFactory.getLogger("Writer");

    @Override
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        ArrayList<WriteCommandOut> writeCommandOuts = new ArrayList<>();
        for (WriteCommandIn writeCommandIn : writeCommandIns) {
            WriteCommandOut writeCommandOut = new WriteCommandOut();
            writeCommandOut.objId = writeCommandIn.objId;
            UserObject obj =
                    new UserObject(writeCommandIn.objId, writeCommandIn.size, writeCommandIn.tag);
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
            ArrayList<Integer> rwUnitIdList =
                    getUnitsNearMiddle(rwDisk, obj.objSize, tag.middleList.get(rwDisk.diskId));

            if (rwUnitIdList == null) {
                log.error("无法为对象" + writeCommandIn.objId + "在磁盘" + rwDisk.diskId
                        + "上找到RW空间, 磁盘unit信息如下");
                log.error("磁盘的RWEnd位置为" + rwDisk.logicalRWEnd + ", 总size为" + rwDisk.unitNum);
                // 打印disk
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rwDisk.logicalRWEnd; i++) {
                    sb.append(rwDisk.unitData.get(i).objId + " ");
                }
                sb.append("\n");
                for (int i = rwDisk.logicalBackStart; i < rwDisk.unitNum; i++) {
                    sb.append(rwDisk.unitData.get(i).objId + " ");
                }
                log.error(sb.toString());
                throw new RuntimeException(
                        "无法为对象" + writeCommandIn.objId + "在磁盘" + rwDisk.diskId + "上找到RW空间");
            }

            Replica rwReplica = new Replica(writeCommandIn.objId, 0, rwDisk.diskId, rwUnitIdList);
            addReplicaToObj(obj, rwReplica);
            for (int id : rwUnitIdList) {
                log.debug("disk中unitid原先的objId: " + rwDisk.unitData.get(id).objId);
            }
            saveReplicaToDisk(rwDisk, rwReplica);

            for (int id : rwUnitIdList) {
                log.debug("disk中unitid更新后的objId: : " + rwDisk.unitData.get(id).objId);
            }
            rwDisk.rwSizeLeft -= obj.objSize;
            rwDisk.RWEnd = Math.min(rwDisk.logicalRWEnd,
                    Math.max(rwDisk.RWEnd, rwUnitIdList.get(rwUnitIdList.size() - 1)));
            tag.sizeList.set(rwDisk.diskId, tag.sizeList.get(rwDisk.diskId) + obj.objSize);
            writeCommandOut.copy1 = new DiskUnit(rwDisk.diskId, rwUnitIdList);


            // 两个备份磁盘
            for (int backReplicaIdx = 1; backReplicaIdx < disks.size(); backReplicaIdx++) {
                log.debug("=== 开始为对象" + writeCommandIn.objId + "的backup replica" + backReplicaIdx
                        + "选择磁盘 ===");
                LocalDisk backupDisk = disks.get(backReplicaIdx);
                ArrayList<Integer> unitIdList = getFreeUnitFromEnd(backupDisk, obj.objSize);

                if (unitIdList == null) {
                    log.error(
                            "无法为对象" + writeCommandIn.objId + "在磁盘" + backupDisk.diskId + "上找到备份空间");
                    log.error("磁盘的RWEnd位置为" + backupDisk.logicalRWEnd + ", 总size为"
                            + backupDisk.unitNum);
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < backupDisk.logicalRWEnd; j++) {
                        sb.append(backupDisk.unitData.get(j).objId + " ");
                    }
                    sb.append("\n");
                    for (int j = backupDisk.logicalBackStart; j < backupDisk.unitNum; j++) {
                        sb.append(backupDisk.unitData.get(j).objId + " ");
                    }
                    log.error(sb.toString());
                    for (int i = 0; i < Info.diskNum; i++) {
                        LocalDisk disk = Info.localDiskTbl.get(i);
                        log.error("磁盘" + i + "的信息: ");
                        log.error("磁盘ID: " + disk.diskId);
                        log.error("磁盘单元数: " + disk.unitNum);
                        log.error("磁盘剩余rw空间: " + disk.rwSizeLeft);
                        log.error("磁盘剩余backup空间: " + disk.backSizeLeft);
                        log.error("磁盘RWEnd: " + disk.RWEnd);
                    }
                    throw new RuntimeException(
                            "无法为对象" + writeCommandIn.objId + "在磁盘" + backupDisk.diskId + "上找到备份空间");
                }

                Replica replica = new Replica(writeCommandIn.objId, backReplicaIdx,
                        backupDisk.diskId, unitIdList);
                addReplicaToObj(obj, replica);
                for (int id : unitIdList) {
                    log.debug("disk中unitid原先的objId: " + backupDisk.unitData.get(id).objId);
                }
                saveReplicaToDisk(backupDisk, replica);
                for (int id : unitIdList) {
                    log.debug("disk中unitid更新后的objId: : " + backupDisk.unitData.get(id).objId);
                }
                backupDisk.backSizeLeft -= obj.objSize;

                if (backReplicaIdx == 1) {
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
     * 获取距离middle最近空闲空间，不维护freespaceBySize
     * 
     * @param obj_size 对象大小，范围1-5
     * @param middle 中间位置
     * @return 空闲空间，用于存放对象。优先返回MAX_RW_END之后的空间，如果没有才返回之前的空间
     */
    private ArrayList<Integer> getUnitsNearMiddle(LocalDisk disk, int obj_size, int middle) {
        log.debug("从磁盘" + disk.diskId + "的" + middle + "位置开始查找空闲空间");
        int leftIdx = middle;
        int rightIdx = middle + 1;

        while (true) {
            // Check for boundary conditions
            if (leftIdx < 0 && rightIdx >= disk.logicalRWEnd) {
                return null;
            }

            // Try to collect all from left side first
            ArrayList<Integer> leftUnits = new ArrayList<>();
            int leftNeeded = obj_size;

            while (leftIdx >= 0 && leftNeeded > 0) {
                if (disk.unitData.get(leftIdx).objId == -1) {
                    leftUnits.add(leftIdx);
                    leftNeeded--;
                }
                leftIdx--;
            }

            // If we found enough units on the left, return them
            if (leftNeeded == 0) {
                return leftUnits;
            }

            // Otherwise try all from right side
            ArrayList<Integer> rightUnits = new ArrayList<>();
            int rightNeeded = obj_size;

            while (rightIdx < disk.logicalRWEnd && rightNeeded > 0) {
                if (disk.unitData.get(rightIdx).objId == -1) {
                    rightUnits.add(rightIdx);
                    rightNeeded--;
                }
                rightIdx++;
            }

            // If we found enough units on the right, return them
            if (rightNeeded == 0) {
                return rightUnits;
            }
        }
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
            }
        }

        // Return null if we couldn't find enough free units
        if (count < objSize) {
            return null;
        }

        return unitIdList;
    }

    /**
     * 基于给定的标签ID和对象大小选择磁盘。 选择一个与标签关联的读写磁盘，以及两个具有最大可用空间的备份磁盘。 <del>1. 函数内部更换tag对应的disk的sizeList</del>
     *
     * @param tagId 标签的ID。
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
