package GC;

import java.util.ArrayList;

import IO.model.GCCommandOut;
import Info.Info;
import Info.model.DiskSpace;
import Info.model.LocalDisk;
import Info.model.TagMeta;
import Info.model.UnitData;
import Info.model.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;


public class GabageCollection {
    private final int gcNum = Info.gcNum;
    ModuleLogger log = LoggerFactory.getLogger("GC");

    /**
     * 垃圾回收入口,每个period调用一次
     */
    public void entry() {
        for (int i = 0; i < Info.diskNum; i++) {
            LocalDisk disk = Info.localDiskTbl.get(i);
            ArrayList<ArrayList<UserObject>> objNotMatch = findMismatch(disk);
            performGC(objNotMatch);
        }
    }

    public GCCommandOut performGC(ArrayList<ArrayList<UserObject>> objNotMatch) {
        if (objNotMatch == null || objNotMatch.isEmpty()) {
            return new GCCommandOut();
        }

        // Track how many swap operations we've used
        int swapsUsed = 0;

        // Process each TagMeta's unmatched objects
        for (int tagMetaIndex = 0; tagMetaIndex < objNotMatch.size() && swapsUsed < gcNum; tagMetaIndex++) {
            ArrayList<UserObject> unmatched = objNotMatch.get(tagMetaIndex);
            
            if (unmatched == null || unmatched.isEmpty()) {
                continue;
            }

            // Sort objects by size to handle larger objects first (reduces fragmentation)
            unmatched.sort((a, b) -> Integer.compare(b.objSize, a.objSize));

            // Try to place each unmatched object
            for (UserObject obj : unmatched) {
                if (swapsUsed >= gcNum) {
                    break;
                }

                // Find the disk containing this object
                int diskIndex = -1;
                int sourceUnitId = -1;
                diskSearch:
                for (int i = 0; i < Info.diskNum; i++) {
                    LocalDisk disk = Info.localDiskTbl.get(i);
                    for (int unit = 0; unit < disk.unitNum; unit++) {
                        if (disk.unitData.get(unit).objId == obj.objId) {
                            diskIndex = i;
                            sourceUnitId = unit;
                            break diskSearch;
                        }
                    }
                }

                if (diskIndex == -1 || sourceUnitId == -1) {
                    continue;
                }

                LocalDisk disk = Info.localDiskTbl.get(diskIndex);
                TagMeta currentTagMeta = disk.tagMetas.get(tagMetaIndex);
                
                // Check if there's space in the target TagMeta region
                int availableSpace = currentTagMeta.right - currentTagMeta.rightNow;
                if (availableSpace < obj.objSize) {
                    log.debug("Not enough space in TagMeta " + currentTagMeta.tagId +
                            " (available: " + availableSpace + ", needed: " + obj.objSize + ")");
                    continue;
                }

                // Find a suitable swap target within the correct TagMeta region
                for (int targetUnitId = currentTagMeta.left; targetUnitId < currentTagMeta.right && swapsUsed < gcNum; targetUnitId++) {
                    // Skip if target unit is the source unit
                    if (targetUnitId == sourceUnitId) {
                        continue;
                    }

                    UnitData targetUnitData = disk.unitData.get(targetUnitId);
                    if (targetUnitData.objId == -1) {
                        // Empty unit - check if we can fit the object here
                        DiskSpace sourceSpace = disk.getSpaceForUnit(sourceUnitId);
                        if (sourceSpace != null) {
                            disk.releaseSpace(sourceSpace);
                            targetUnitData.objId = obj.objId;
                            currentTagMeta.sizeNow += obj.objSize;
                            currentTagMeta.rightNow = Math.max(currentTagMeta.rightNow, targetUnitId + 1);
                            swapsUsed++;
                            break;
                        }
                    } else {
                        // Try to swap with existing object
                        UserObject targetObj = disk.getObjOfUnit(targetUnitId);
                        if (targetObj != null) {
                            // Find target object's TagMeta to check its space
                            TagMeta targetTagMeta = disk.getTagMetaByTagId(targetObj.objTag);
                            if (targetTagMeta != null) {
                                int targetAvailableSpace = targetTagMeta.right - targetTagMeta.rightNow;
                                if (targetAvailableSpace >= targetObj.objSize && targetObj.objSize <= obj.objSize) {
                                    // Swap the objects
                                    UnitData sourceUnitData = disk.unitData.get(sourceUnitId);
                                    int tempObjId = sourceUnitData.objId;
                                    sourceUnitData.objId = targetUnitData.objId;
                                    targetUnitData.objId = tempObjId;
                                    
                                    // Update size tracking for both TagMetas
                                    currentTagMeta.sizeNow += obj.objSize;
                                    targetTagMeta.sizeNow += targetObj.objSize;
                                    currentTagMeta.rightNow = Math.max(currentTagMeta.rightNow, targetUnitId + 1);
                                    targetTagMeta.rightNow = Math.max(targetTagMeta.rightNow, sourceUnitId + 1);
                                    
                                    swapsUsed++;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    

    /**
     * 查找不匹配的对象
     * @param disk
     * @return
     */
    public ArrayList<ArrayList<UserObject>> findMismatch(LocalDisk disk) {
        // 1级索引对应tagMeta的索引，二级存放不匹配的obj
        ArrayList<ArrayList<UserObject>> objNotMatch = new ArrayList<>();
        for (int i = 0; i < disk.tagMetas.size(); i++) {
            // 2. 找到不匹配的Tag，遍历tagMetas
            TagMeta legalTagMeta = disk.tagMetas.get(i);
            for (int unitId = legalTagMeta.left; unitId < legalTagMeta.right; unitId++) {
                // int idOfThisUnit
                int tagIdOfUnit = disk.getTagIdOfUnit(unitId);
                if (tagIdOfUnit != legalTagMeta.tagId) {
                    UserObject obj = disk.getObjOfUnit(unitId);
                    if (objNotMatch.size() <= i) {
                        objNotMatch.add(new ArrayList<>());
                    }
                    objNotMatch.get(i).add(obj);
                }
            }
        }
        return objNotMatch;
    }
}
