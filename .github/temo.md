```java
    /**
     * 从磁盘末尾获取空闲空间
     * 
     * @param obj_size 需要分配的对象大小（1-5）
     * @return 分配的空间（优先使用磁盘尾部空间）
     */
    public DiskSpace getSpaceFromEnd(int obj_size) {
        DiskSpace space = findSpaceFromEnd(obj_size);
        if (space == null) {
            log.debug("磁盘" + diskId + "没有找到合适的空间存放备份replica");
            return null;
        }

        sizeLeft -= obj_size;

        // 刚好满足大小直接使用
        if (space.size == obj_size) {
            space.isFree = false;
            space.type = DiskSpaceType.BACKUPSPACE;
            for (int i = space.start; i <= space.end; i++) {
                unitData.get(i).space = space;
                unitData.get(i).objId = -1;
                unitData.get(i).blockId = -1;
            }
            return space;
        }

        // 空间足够大时进行切割（从尾部切割）
        int newStart = space.end - obj_size + 1;
        DiskSpace remainingSpace = new DiskSpace(true, space.start, newStart - 1, diskId);
        remainingSpace.type = DiskSpaceType.UNUSED;

        // 更新原始空间信息
        space.setStartAndEnd(newStart, space.end);
        space.isFree = false;
        space.type = DiskSpaceType.BACKUPSPACE;

        // 更新单元信息
        for (int i = space.start; i <= space.end; i++) {
            unitData.get(i).space = space;
            unitData.get(i).objId = -1;
            unitData.get(i).blockId = -1;
            // log.debug("111111设置位置" + i + "的space=" + unitData.get(i).space);
        }
        for (int i = remainingSpace.start; i <= remainingSpace.end; i++) {
            unitData.get(i).space = remainingSpace;
            unitData.get(i).objId = -1;
            unitData.get(i).blockId = -1;
            // log.debug("222222设置位置" + i + "的space=" + unitData.get(i).space);
        }

        return space;
    }



    public ArrayList<Integer> getUnitsFromEndTag(int obj_size, int obj_id) {
        if (sizeLeft < obj_size) {
            log.debug("没有足够的空间，obj_size = " + obj_size + ", sizeLeft = " + sizeLeft);
            return null;
        }
        ArrayList<Integer> unitIdList = new ArrayList<>();

        int end = unitNum - 1;
        int neededSize = obj_size;

        while (neededSize != 0) {
            DiskSpace space = unitData.get(end).space;
            end = space.start - 1;

            if (space.isFree) {
                continue;
            }

            space.isFree = false;
            space.type = DiskSpaceType.BACKUPSPACE;
            if (neededSize - space.size >= 0) {
                // 占用space的所有空间
                for (int i = space.start; i <= space.end; i++) {
                    unitData.get(i).space = space;
                    unitData.get(i).objId = obj_id;
                    unitIdList.add(i);
                }
                neededSize -= space.size;
            } else if (neededSize - space.size < 0) {
                // 需要切割空间，拆分成(space.size-neededSize, neededSize)两部分
                int newFreeStart = space.start;
                int newFreeEnd = space.end - neededSize;
                DiskSpace newFreeSpace = new DiskSpace(true, newFreeStart, newFreeEnd, diskId);
                newFreeSpace.type = DiskSpaceType.UNUSED;
                for (int i = newFreeStart; i <= newFreeEnd; i++) {
                    unitData.get(i).space = newFreeSpace;
                    unitData.get(i).objId = -1;
                    unitData.get(i).blockId = -1;
                }
                // 更新原始空间信息
                space.setStartAndEnd(space.end - neededSize + 1, space.end);
                for (int i = space.start; i <= space.end; i++) {
                    unitData.get(i).space = space;
                    unitData.get(i).objId = obj_id;
                    unitIdList.add(i);
                }
                neededSize = 0;
                break;
            }
        }

        // 更新RWEnd
        if (RWEnd < unitIdList.get(unitIdList.size() - 1)) {
            RWEnd = unitIdList.get(unitIdList.size() - 1);
        }
        // 更新sizeLeft
        sizeLeft -= obj_size;
        return unitIdList;
    }

for (int i = 1; i < disks.size(); i++) {
        ArrayList<Integer> unitIdList = getFreeUnitFromEnd(rwDisk, i, i);
        LocalDisk backupDisk = disks.get(i);
        space = backupDisk.getSpaceFromEnd(obj.objSize);
        if (space != null) {
            ArrayList<Integer> unitIdList = new ArrayList<>();
            for (int j = 0; j < space.size; j++) {
                unitIdList.add(space.start + j);
            }
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
        } else {
            ArrayList<Integer> unitIdList = backupDisk.getUnitsFromEndTag(obj.objSize, obj.objId);
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

```
