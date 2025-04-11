package Writer;

import java.util.ArrayList;
import java.util.HashSet;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import IO.model.DiskUnit;
import Info.Info;
import Info.model.*;
import Writer.TagDistribution.TagDistribution;

public class MakeTagGreatAgain extends DefaultWriteStrategy {
    // TagDistribution实例，负责所有tag相关操作
    private TagDistribution tagDistribution;

    @Override
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        ArrayList<WriteCommandOut> writeCommandOuts = new ArrayList<>();
        for (WriteCommandIn writeCommandIn : writeCommandIns) {
            log.debug("开始处理写入请求 " + writeCommandIn.objId);
            // 开始处理写入请求
            WriteCommandOut writeCommandOut = new WriteCommandOut();
            writeCommandOut.objId = writeCommandIn.objId;
            UserObject obj = new UserObject(writeCommandIn.objId, writeCommandIn.size, writeCommandIn.tag);
            Info.objMap.put(writeCommandIn.objId, obj);

            // 获取磁盘，使用本地的selectDiskByTag方法
            ArrayList<LocalDisk> disks = selectDiskByTag(writeCommandIn.tag, writeCommandIn.size);
            if (disks == null || disks.size() < 3) {
                log.error("没有找到可用的磁盘");
                throw new RuntimeException("没有找到可用的磁盘");
            }
            LocalDisk rwDisk = disks.get(0);
            log.debug("=== 开始为对象" + writeCommandIn.objId + "在磁盘 " + rwDisk.diskId + "选择Unit ===");
            ArrayList<DiskSpace> diskSpaces = getFreeSpaceByTag(writeCommandIn.tag, rwDisk, writeCommandIn.size);

            boolean notFoundSpaceForTag = (diskSpaces == null);
            if (notFoundSpaceForTag) {
                log.error("无法为对象" + writeCommandIn.objId + "在磁盘" + rwDisk.diskId + "找到tagid = "
                        + writeCommandIn.tag + "读写空间, 尝试再在相似tag的空中寻找空间");
                ArrayList<Integer> similarTags = tagDistribution.getSimilarTags(writeCommandIn.tag);
                ArrayList<Integer> otherTags = new ArrayList<>();

                // 向otherTags中添加所有tagId，但不包含similarTags中的tagId
                for (TagMeta tagMeta : rwDisk.tagMetas) {
                    if (tagMeta.tagId != writeCommandIn.tag && !similarTags.contains(tagMeta.tagId)) {
                        otherTags.add(tagMeta.tagId);
                    }
                }

                if (similarTags != null) {
                    for (Integer similarTagId : similarTags) {
                        if (similarTagId == writeCommandIn.tag)
                            continue;
                        TagMeta similarTagMeta = rwDisk.getTagMetaByTagId(similarTagId);
                        if (similarTagMeta == null)
                            continue;
                        ArrayList<DiskSpace> spaces = getFreeSpaceByTag(similarTagId, rwDisk, writeCommandIn.size);
                        if (spaces != null) {
                            log.debug("找到similarTagid = " + similarTagId + "的空间: " + spaces.toString());
                            diskSpaces = spaces;
                            notFoundSpaceForTag = false;
                            break;
                        }
                    }
                }

                if (otherTags != null && diskSpaces == null) {
                    for (Integer otherTagId : otherTags) {
                        TagMeta otherTagMeta = rwDisk.getTagMetaByTagId(otherTagId);
                        if (otherTagMeta == null)
                            continue;
                        ArrayList<DiskSpace> spaces = getFreeSpaceByTag(otherTagId, rwDisk, writeCommandIn.size);
                        if (spaces != null) {
                            log.debug("找到otherTagId = " + otherTagId + "的空间: " + spaces.toString());
                            diskSpaces = spaces;
                            break;
                        }
                    }
                }

                if (diskSpaces == null) {
                    // 输出整个磁盘unitData
                    StringBuilder sb = new StringBuilder();
                    for (UnitData unit : rwDisk.unitData) {
                        sb.append(unit.objId).append(" ");
                    }
                    log.error("在磁盘" + rwDisk.diskId + "无法找到空间，unitData: " + sb.toString());
                    throw new RuntimeException(
                            "无法为对象" + writeCommandIn.objId + "在磁盘" + rwDisk.diskId + "找到读写空间");
                }
            }
            // 从diskSpaces中拿出所有unitId
            ArrayList<Integer> unitIdList = new ArrayList<>();
            int maxUnitId = Integer.MIN_VALUE;
            for (DiskSpace diskSpace : diskSpaces) {
                for (int i = diskSpace.start; i <= diskSpace.end; i++) {
                    unitIdList.add(i);
                    if (i > maxUnitId) {
                        maxUnitId = i;
                    }
                }
                // 更新tagMeta的sizeNow
            }
            rwDisk.getTagMetaByTagId(obj.objTag).sizeNow += obj.objSize;
            // 分配空间
            Replica replica = new Replica(writeCommandIn.objId, 0, rwDisk.diskId, unitIdList);
            addReplicaToObj(obj, replica);
            saveReplicaToDisk(rwDisk, replica);
            writeCommandOut.copy1 = new DiskUnit(rwDisk.diskId, unitIdList);
            rwDisk.rwSizeLeft -= obj.objSize;
            // 维护RWEnd
            rwDisk.RWEnd = Math.min(rwDisk.logicalRWEnd, Math.max(rwDisk.RWEnd, maxUnitId));
            // 维护tagMeta的rightNow
            TagMeta tagMeta = rwDisk.getTagMetaByTagId(writeCommandIn.tag);
            if (tagMeta != null && maxUnitId > tagMeta.rightNow && maxUnitId < tagMeta.right) {
                tagMeta.rightNow = maxUnitId;
            }
            // 两个备份磁盘
            for (int i = 1; i < disks.size(); i++) {
                log.debug("=== 开始为对象" + writeCommandIn.objId + "的backup replica" + i + "选择Unit ===");
                LocalDisk backupDisk = disks.get(i);
                ArrayList<Integer> unitIds = getFreeUnitFromEnd(backupDisk, obj.objSize);

                if (unitIds == null) {
                    log.error("无法为对象" + writeCommandIn.objId + "在磁盘" + backupDisk.diskId + "上找到备份空间");
                    throw new RuntimeException(
                            "无法为对象" + writeCommandIn.objId + "在磁盘" + backupDisk.diskId + "上找到备份空间");
                }

                Replica replicaBack = new Replica(writeCommandIn.objId, i, backupDisk.diskId, unitIds);
                addReplicaToObj(obj, replicaBack);
                for (int id : unitIds) {
                    log.debug("disk中unitid原先的objId: " + backupDisk.unitData.get(id).objId);
                }
                saveReplicaToDisk(backupDisk, replicaBack); // BUG 疑似这里设置ObjId没生效
                for (int id : unitIds) {
                    log.debug("disk中unitid更新后的objId: : " + backupDisk.unitData.get(id).objId);
                }
                backupDisk.backSizeLeft -= obj.objSize;

                if (i == 1) {
                    writeCommandOut.copy2 = new DiskUnit(backupDisk.diskId, unitIds);
                } else {
                    writeCommandOut.copy3 = new DiskUnit(backupDisk.diskId, unitIds);
                }
            }
            // log.debug("处理对象id: " + writeCommandIn.objId + " 的writeCommandOut: " +
            // writeCommandOut.toString());

            writeCommandOuts.add(writeCommandOut);
        }
        return writeCommandOuts;
    }

    // 构造函数
    public MakeTagGreatAgain() {
        // 初始化TagDistribution
        tagDistribution = new TagDistribution();
    }

    /**
     * 基于给定的标签ID和对象大小选择磁盘。 选择一个与标签关联的读写磁盘，以及两个具有最大可用空间的备份磁盘。
     *
     * @param tagId   标签的ID。
     * @param objSize 要写入的对象的大小。
     * @return 一个包含所选 LocalDisk 对象的 ArrayList。 列表中的第一个磁盘是读写磁盘，后跟两个备份磁盘（如果可用）。
     */
    private ArrayList<LocalDisk> selectDiskByTag(int tagId, int objSize) {
        ArrayList<LocalDisk> candidateDisks = new ArrayList<>(3);

        // select disks based on tag information
        Tag tag = Info.tags.get(tagId);
        // 遍历有tag的磁盘
        LocalDisk rwDisk = null;
        int minSize = Integer.MAX_VALUE;
        for (int i : tag.diskIdList) {
            LocalDisk disk = Info.localDiskTbl.get(i);
            // 找到剩余空间最大的磁盘
            int sizeNow = disk.getTagMetaByTagId(tagId).sizeNow;
            if (sizeNow < minSize) {
                minSize = sizeNow;
                rwDisk = disk;
            }
        }

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
            throw new RuntimeException("没有找到可用的磁盘");
        }
        candidateDisks.add(rwDisk);
        candidateDisks.add(backupDisk1);
        candidateDisks.add(backupDisk2);
        log.debug("candidateDisks: " + candidateDisks.toString());
        return candidateDisks;
    }

    // 获取指定tag在磁盘上的空闲空间
    public ArrayList<DiskSpace> getFreeSpaceByTag(int tagId, LocalDisk disk, int size) {
        HashSet<DiskSpace> diskSpaces = new HashSet<>();
        TagMeta tagMeta = disk.getTagMetaByTagId(tagId);
        if (tagMeta == null) {
            return null;
        }
        int left = tagMeta.left;
        int right = tagMeta.right;
        int rightNow = tagMeta.rightNow;

        log.debug("tagMeta: " + tagMeta.toString());

        boolean finishFlag = false;

        // 第一层级，从left开始扫空间，找到第一个能装得下的空间
        for (int i = left; i <= right; i++) {
            DiskSpace diskSpace = disk.getSpaceForUnit(i);
            if (diskSpace.size >= size && diskSpace.isFree) {
                if (diskSpace.size == size) {
                    log.debug("找到尺寸刚好的空间: " + diskSpace.toString());
                    diskSpaces.add(diskSpace);
                    diskSpace.isFree = false;
                    finishFlag = true;
                    break;
                } else {
                    // 切分空间
                    log.debug("切分空间: " + diskSpace.toString());
                    int end = diskSpace.start + size - 1;
                    int startNext = end + 1;
                    int tagIdByIndex = disk.getTagMetaByIndex(startNext);
                    DiskSpace space2remain = new DiskSpace(true, startNext, diskSpace.end, disk.diskId, tagIdByIndex);
                    diskSpace.setStartAndEnd(diskSpace.start, end);
                    diskSpace.isFree = false;
                    log.debug("切分空间完成: " + diskSpace.toString() + " 剩余空间: " + space2remain.toString());
                    for (int j = diskSpace.start; j <= end; j++) {
                        disk.unitData.get(j).space = diskSpace;
                        disk.unitData.get(j).objId = -1;
                        disk.unitData.get(j).blockId = -1;
                        // log.debug("将disk" + disk.diskId + "的unitData[" + j
                        // + "]的objId和blockId设置为: " + -1 + ", " + -1);
                    }
                    for (int j = startNext; j <= space2remain.end; j++) {
                        disk.unitData.get(j).space = space2remain;
                        disk.unitData.get(j).objId = -1;
                        disk.unitData.get(j).blockId = -1;
                        // log.debug("将disk" + disk.diskId + "的unitData[" + j
                        // + "]的objId和blockId设置为: " + -1 + ", " + -1);
                    }

                    finishFlag = true;
                    diskSpaces.add(diskSpace);
                    log.debug("切割空间完成: " + diskSpace.toString());
                    break;
                }
            } else {
                // log.debug("当前空间不匹配: " + diskSpace.toString());
            }
        }
        if (finishFlag) {
            return new ArrayList<>(diskSpaces);
        }

        // 第二层级，使用滑动窗口找到最短距离的空间组合
        int minSpan = Integer.MAX_VALUE; // 记录最小跨度
        int bestStart = -1; // 最佳起始位置
        int bestEnd = -1; // 最佳结束位置

        int windowStart = left;
        int windowEnd = left;
        int currentSize = 0;

        // 使用滑动窗口遍历
        while (windowEnd <= right) {
            // 如果当前窗口中的空闲空间不够，继续扩大窗口
            while (windowEnd <= right && currentSize < size) {
                if (disk.unitData.get(windowEnd).objId == -1) {
                    currentSize++;
                }
                windowEnd++;
            }

            // 如果找到了足够的空间
            if (currentSize == size) {
                int span = windowEnd - windowStart;
                if (span < minSpan) {
                    minSpan = span;
                    bestStart = windowStart;
                    bestEnd = windowEnd - 1;
                }
            }

            // 缩小窗口左边界
            if (disk.unitData.get(windowStart).objId == -1) {
                currentSize--;
            }
            windowStart++;
            while (windowStart <= right && disk.unitData.get(windowStart).objId != -1) {
                windowStart++;
            }
        }

        // 如果找到了合适的空间
        if (bestStart != -1) {
            log.debug("滑动窗口，找到合适的空间: " + bestStart + " " + bestEnd + "此时，minSpan=" + minSpan
                    + ", currentSize=" + currentSize);
            // 遍历从bestStart到bestEnd，找到所有对应空间
            int sizeLeft = size; // 剩余空间
            for (int i = bestStart; i <= bestEnd; i++) {
                DiskSpace diskSpace = disk.getSpaceForUnit(i);
                if (!diskSpace.isFree) {
                    continue;
                }
                if (diskSpace.size < sizeLeft) {
                    diskSpaces.add(diskSpace);
                    diskSpace.isFree = false;
                    sizeLeft -= diskSpace.size;
                } else {
                    // 出现这种情况一定是最后一个空间
                    // 切分空间
                    int end = diskSpace.start + sizeLeft - 1;
                    int startNext = end + 1;
                    int tagIdByIndex = disk.getTagMetaByIndex(startNext);
                    DiskSpace space2remain = new DiskSpace(true, startNext, diskSpace.end, disk.diskId, tagIdByIndex);
                    diskSpace.setStartAndEnd(diskSpace.start, end);
                    diskSpace.isFree = false;
                    for (int j = diskSpace.start; j <= end; j++) {
                        disk.unitData.get(j).space = diskSpace;
                        disk.unitData.get(j).objId = -1;
                        disk.unitData.get(j).blockId = -1;
                        // log.debug("将disk" + disk.diskId + "的unitData[" + j
                        // + "]的objId和blockId设置为: " + -1 + ", " + -1);
                    }
                    for (int j = startNext; j <= space2remain.end; j++) {
                        disk.unitData.get(j).space = space2remain;
                        disk.unitData.get(j).objId = -1;
                        disk.unitData.get(j).blockId = -1;
                        // log.debug("将disk" + disk.diskId + "的unitData[" + j
                        // + "]的objId和blockId设置为: " + -1 + ", " + -1);
                    }
                    diskSpaces.add(diskSpace);
                    break;
                }
            }
            return new ArrayList<>(diskSpaces);
        }

        return null;
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
                // log.debug("Found free unit: disk=" + disk.diskId + ", unit=" + i
                // + ", current objId=" + disk.unitData.get(i).objId);
                unitIdList.add(i);
                count++;
            } else {
                // Log when we encounter allocated units
                // log.debug("Skipping allocated unit: disk=" + disk.diskId + ", unit=" + i
                // + ", used by objId=" + disk.unitData.get(i).objId);
            }
        }

        // Return null if we couldn't find enough free units
        if (count < objSize) {
            return null;
        }

        return unitIdList;
    }
}
