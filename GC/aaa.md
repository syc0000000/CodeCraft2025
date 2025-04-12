```java
        // 从后往前扫,找到第一个obj
        while (true) {
            UserObject obj = null;
            for (int i = tagMeta.right; i >= tagMeta.left; i--) {
                if (disk.unitData.get(i).objId != -1) {
                    obj = disk.getObjOfUnit(i);
                    break;
                }
            }
            if (obj == null) {
                break;
            }
            if (gcUsed + obj.objSize > gcNum) {
                break;
            }
            // 找到空间，进行GC,使用ff
            ArrayList<DiskSpace> diskSpaces = new ArrayList<>();
            int sizeLeft = obj.objSize;
            for (int i = tagMeta.left; i <= obj.replicas.get(0).unitIdList.get(0) - 1;) {
                if (!disk.unitData.get(i).space.isFree) {
                    i++;
                    continue;
                }
                diskSpaces.add(disk.unitData.get(i).space);
                i = disk.unitData.get(i).space.end + 1;
                sizeLeft -= disk.unitData.get(i).space.size;
                if (sizeLeft <= 0) {
                    break;
                }
            }
            if (sizeLeft > 0) {
                break;
            }
            int blockId = 0;
            // 分配空间
            ArrayList<Integer> unitIdList = new ArrayList<>();

            for (int i = 0; i < diskSpaces.size() - 1; i++) {
                // 在最后一个space之前,说明都是要完全占用的
                diskSpaces.get(i).isFree = false;
                for (int j = diskSpaces.get(i).start; j <= diskSpaces.get(i).end; j++) {
                    disk.unitData.get(j).objId = obj.objId;
                    disk.unitData.get(j).blockId = blockId++;
                    unitIdList.add(j);
                }
            }
            // 最后一个space,需要切分
            int end = diskSpaces.get(diskSpaces.size() - 1).start + sizeLeft - 1;
            int startNext = end + 1;
            int tagIdByIndex = disk.getTagMetaByIndex(startNext);
            DiskSpace space2left = new DiskSpace(true, startNext, diskSpaces.get(diskSpaces.size() - 1).end,
                    disk.diskId, tagIdByIndex);
            diskSpaces.get(diskSpaces.size() - 1).setStartAndEnd(diskSpaces.get(diskSpaces.size() - 1).start, end);
            diskSpaces.get(diskSpaces.size() - 1).isFree = false;
            for (int j = diskSpaces.get(diskSpaces.size() - 1).start; j <= end; j++) {
                disk.unitData.get(j).objId = obj.objId;
                disk.unitData.get(j).blockId = blockId++;
                unitIdList.add(j);
            }
            for (int j = startNext; j <= space2left.end; j++) {
                disk.unitData.get(j).space = space2left;
                disk.unitData.get(j).objId = -1;
                disk.unitData.get(j).blockId = -1;
            }
            disk.rwSizeLeft -= obj.objSize;
            // 维护RWEnd
            disk.RWEnd = Math.min(disk.logicalRWEnd, Math.max(disk.RWEnd, end));
            Replica replica = new Replica(obj.objId, 0, disk.diskId, unitIdList);
            addReplicaToObj(obj, replica);
            saveReplicaToDisk(disk, replica);
            // 从disk中移除obj
            removeFromDisk(obj.replicas.get(0).unitIdList, disk, obj.objId);
```