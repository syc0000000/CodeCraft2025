package GC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;

import IO.model.GCCommandOut;
import Info.Info;
import Info.model.DiskSpace;
import Info.model.LocalDisk;
import Info.model.ReadTask;
import Info.model.Replica;
import Info.model.TagMeta;
import Info.model.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class GabageCollection {
    private final static int gcNum = Info.gcNum;
    private final static ModuleLogger log = LoggerFactory.getLogger("GC");

    /**
     * 垃圾回收入口,每个period调用一次
     */
    public static ArrayList<GCCommandOut> entry() {
        ArrayList<GCCommandOut> gcCommandOuts = new ArrayList<>();
        for (int i = 0; i < Info.diskNum; i++) {
            LocalDisk disk = Info.localDiskTbl.get(i);
            HashSet<UserObject> objNotMatch = findMismatch(disk);
            GCCommandOut out = performGC(objNotMatch, disk);
            gcCommandOuts.add(out);
        }
        return gcCommandOuts;
    }

    public static GCCommandOut performGC(HashSet<UserObject> objNotMatch, LocalDisk disk) {
        // Track how many swap operations we've used
        int gcUsed = 0;

        GCCommandOut gcCommandOut = new GCCommandOut();
        // Process each TagMeta's unmatched objects
        ArrayList<UserObject> unmatched = new ArrayList<>(objNotMatch);

        if (unmatched == null || unmatched.isEmpty()) {
            return gcCommandOut;
        }

        // Sort objects by size to handle larger objects first (reduces fragmentation)
        unmatched.sort((a, b) -> Integer.compare(b.objSize, a.objSize));

        // Try to place each unmatched object
        for (UserObject obj : unmatched) {
            if (gcUsed + obj.objSize > gcNum) {
                continue;
            }

            // 1. 删除

            // 2. 重写
            TagMeta tagMetaOfObj = disk.getTagMetaByTagId(obj.objTag);
            ArrayList<DiskSpace> diskSpaces = findFreeSpaceByTag(obj.objTag, disk, obj.objSize);
            if (diskSpaces == null) {
                log.debug("没有找到合适的空间，不GC，objId: " + obj.objId + ", objTag: " + obj.objTag);
                continue;
            }
            // 找到空间，进行GC
            log.debug("找到合适的空间，进行GC，objId: " + obj.objId + ", objTag: " + obj.objTag);

            Replica replicaBefore = obj.replicas.get(0);
            // deep copy unitIdList
            ArrayList<Integer> unitIdListBefore = new ArrayList<>(replicaBefore.unitIdList);
            log.debug("unitIdListBefore: " + unitIdListBefore.toString());

            // 分配空间
            ArrayList<Integer> unitIdListAfter = new ArrayList<>();
            int maxUnitId = Integer.MIN_VALUE;
            for (DiskSpace diskSpace : diskSpaces) {
                for (int i = diskSpace.start; i <= diskSpace.end; i++) {
                    unitIdListAfter.add(i);
                    if (i > maxUnitId) {
                        maxUnitId = i;
                    }
                }
                // 更新tagMeta的sizeNow
            }
            // unitidList 排序，从小到大
            Collections.sort(unitIdListAfter);
            disk.getTagMetaByTagId(obj.objTag).sizeNow += obj.objSize;
            // 分配空间
            Replica replicaAfter = new Replica(obj.objId, 0, disk.diskId, unitIdListAfter);
            addReplicaToObj(obj, replicaAfter);
            saveReplicaToDisk(disk, replicaAfter);
            disk.rwSizeLeft -= obj.objSize;
            // 维护RWEnd
            disk.RWEnd = Math.min(disk.logicalRWEnd, Math.max(disk.RWEnd, maxUnitId));
            // 维护tagMeta的rightNow
            TagMeta tagMeta = disk.getTagMetaByTagId(tagMetaOfObj.tagId);
            if (tagMeta != null && maxUnitId > tagMeta.rightNow && maxUnitId < tagMeta.right) {
                tagMeta.rightNow = maxUnitId;
            }

            ArrayList<Integer> unitIDList = replicaAfter.unitIdList;
            log.debug("unitIDList: " + unitIDList.toString());
            // 刷新Obj的isInTask情况
            // 先全部清空
            for (int j = 0; j < obj.objSize; j++) {
                // 设置单元中的isInTask为false
                disk.unitData.get(unitIDList.get(j)).isInTask = false;
            }
            LinkedList<ReadTask> readTasks = obj.readTasks;
            // 然后根据readtask中的blockNotFinished情况，设置isInTask为true
            for (ReadTask readTask : readTasks) {
                for (int blockId : readTask.blockNotFinished) {
                    disk.unitData.get(unitIDList.get(blockId)).isInTask = true;
                }
            }

            removeFromDisk(unitIdListBefore, disk, obj.objId);

            // 3. 添加GCCommandOut
            gcCommandOut.size += obj.objSize;
            gcCommandOut.s.addAll(unitIdListBefore);
            gcCommandOut.t.addAll(unitIdList);
            gcUsed += obj.objSize;
        }
        return gcCommandOut;
    }

    private static ArrayList<DiskSpace> findFreeSpaceByTag(int tagId, LocalDisk disk, int size) {
        HashSet<DiskSpace> diskSpaces = new HashSet<>();
        TagMeta tagMeta = disk.getTagMetaByTagId(tagId);
        if (tagMeta == null) {
            return null;
        }
        int left = tagMeta.left;
        int right = tagMeta.right;

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
     * 查找不匹配的对象
     * 
     * @param disk
     * @return
     */
    public static HashSet<UserObject> findMismatch(LocalDisk disk) {
        // 1级索引对应tagMeta的顺序，二级存放不匹配的obj
        HashSet<UserObject> objNotMatch = new HashSet<>();

        for (int i = 0; i < disk.tagMetas.size(); i++) {
            // 2. 找到不匹配的Tag，遍历tagMetas
            TagMeta legalTagMeta = disk.tagMetas.get(i);
            for (int unitId = legalTagMeta.left; unitId < legalTagMeta.right; unitId++) {
                // int idOfThisUnit
                if (disk.unitData.get(unitId).objId == -1) {
                    continue;
                }

                int tagIdOfUnit = disk.getTagIdOfUnit(unitId);
                if (tagIdOfUnit != legalTagMeta.tagId) {
                    UserObject obj = disk.getObjOfUnit(unitId);
                    log.debug("找到不匹配的对象: " + obj.toString() + ", tagIdOfUnit: " + tagIdOfUnit
                            + ", legalTagMeta.tagId: " + legalTagMeta.tagId);
                    objNotMatch.add(obj);
                }
            }
        }
        return objNotMatch;
    }

    protected static void addReplicaToObj(UserObject obj, Replica replica) {
        log.debug("添加副本到对象: objId=" + obj.objId + ", replicaId=" + replica.replicaId);
        obj.replicas.set(replica.replicaId, replica);
    }

    protected static void saveReplicaToDisk(LocalDisk disk, Replica replica) {
        log.debug("(saveReplicaToDisk) 保存副本到磁盘: diskId=" + disk.diskId + ", replica=" + replica);
        for (int i = 0; i < replica.unitIdList.size(); i++) {
            log.debug("将disk" + disk.diskId + "的unitData[" + replica.unitIdList.get(i)
                    + "]的objId和blockId设置为: " + replica.objId + ", " + i);
            disk.unitData.get(replica.unitIdList.get(i)).objId = replica.objId;
            disk.unitData.get(replica.unitIdList.get(i)).blockId = i;
        }
    }

    /**
     * 负责释放指定空间 维护的信息,unitData
     * 
     * @param obj_id
     * @return
     */
    private static void removeFromDisk(ArrayList<Integer> unit_ids, LocalDisk rwDisk, int obj_id) {
        log.debug("准备释放 Obj_ID = " + obj_id + " 所占用的空间");
        for (int unit_id : unit_ids) {
            DiskSpace space = rwDisk.unitData.get(unit_id).space;
            // 更新rwend
            if (space.end == rwDisk.RWEnd) {
                while (rwDisk.RWEnd > 0) {
                    rwDisk.RWEnd--;
                }
            }
            rwDisk.unitData.get(unit_id).objId = -1;
            rwDisk.unitData.get(unit_id).blockId = -1;
            rwDisk.unitData.get(unit_id).isInTask = false;
            rwDisk.rwSizeLeft += 1;
            releaseSpace(space);
            // 更新tagMeta的sizeNow
            rwDisk.getTagMetaByTagId(Info.objMap.get(obj_id).objTag).sizeNow -= 1;
        }
    }

    /**
     * 释放空间
     * 
     * @param space
     */
    private static void releaseSpace(DiskSpace space) {
        log.debug("释放空间: " + space);
        LocalDisk disk = Info.localDiskTbl.get(space.diskId);
        int diskId = disk.diskId;
        if (space.diskId != diskId) {
            log.error("释放空间: " + space + " 不是本磁盘，异常");
            return;// 不是本磁盘，异常报错
        }

        // isFree = true
        space.isFree = true;
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
