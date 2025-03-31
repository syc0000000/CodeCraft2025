package Info.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;
import Info.Info;

/**
 * 本地磁盘类 - 存储磁盘信息
 */
public class LocalDisk {
    private static final ModuleLogger log = LoggerFactory.getLogger("LocalDisk");

    public int diskId; // 磁盘id
    public int unitNum; // 存储单元数量
    public int ptr; // 当前磁头指针位置

    public int RWEnd; // 读写空间结束位置
    public int sizeLeft; // 剩余空间大小

    public Action preoper; // 上一次操作
    public int pretoken; // 上一次令牌数量

    // 优化: 按大小组织空闲空间的集合
    // key: 空间大小1-5, value: 该大小的空闲空间列表
    public HashMap<Integer, TreeSet<DiskSpace>> freespaceBySize;

    // 存储单元数据（对象ID, -1表示空）
    public ArrayList<UnitData> unitData;

    // ******** WriteStrategy: Unit FF需要使用的变量 ********
    public TreeSet<DiskSpace> freespaceNotBySize; // 可用单元ID集合

    // ******** WriteStrategy: Tag需要使用的变量 ********
    /** 人为限制的读写区域边界，RW Replica写入的范围只能在[0,RWEnd]范围内，在初始化之后不再修改 */
    public int logicalRWEnd;
    /** 人为限制的备份区域边界，Backup Replica写入的范围只能在[BackStart, unitNum - 1]范围内，在初始化之后不再修改 */
    public int logicalBackStart;
    /** 备份区的剩余空间，需要在删写时维护 */
    public int backSizeLeft;
    /** 读写区的剩余空间，需要在删写时维护 */
    public int rwSizeLeft;

    public static LocalDisk createDisk(int diskId, int unitNum, String type) {
        LocalDisk disk = new LocalDisk(diskId, unitNum);
        switch (type) {
            case "unit":
                disk.freespaceNotBySize = new TreeSet<>(DiskSpace.comparator);
                // diskspace size均为1
                for (int i = 0; i < unitNum; i++) {
                    DiskSpace space = new DiskSpace(true, i, i, diskId);
                    disk.unitData.add(new UnitData(-1, -1, space));
                    disk.freespaceNotBySize.add(space);
                }
                break;
            case "space":
                // 创建初始空闲空间
                DiskSpace initialSpace = new DiskSpace(true, 0, unitNum - 1, diskId);
                // 添加到按大小组织的集合
                disk.freespaceBySize.put(1, new TreeSet<>(DiskSpace.comparator));
                disk.freespaceBySize.put(2, new TreeSet<>(DiskSpace.comparator));
                disk.freespaceBySize.put(3, new TreeSet<>(DiskSpace.comparator));
                disk.freespaceBySize.put(4, new TreeSet<>(DiskSpace.comparator));
                disk.freespaceBySize.put(5, new TreeSet<>(DiskSpace.comparator));
                disk.freespaceBySize.get(5).add(initialSpace);

                // 更新单元到空间的映射
                for (int i = 0; i < unitNum; i++) {
                    disk.unitData.add(new UnitData(-1, -1, initialSpace));
                }
                break;
            case "tag":
                // 初始化rw,backup边界
                disk.logicalRWEnd = (int) (unitNum / 2.91) - 1;
                disk.logicalBackStart = disk.logicalRWEnd + 1;
                // 初始化剩余空间
                disk.backSizeLeft = unitNum - disk.logicalBackStart;
                disk.rwSizeLeft = disk.logicalRWEnd + 1;
                // 整个backup区域算作一个space，后续不会再对它进行切分
                DiskSpace backupDiskSpace = new DiskSpace(false, disk.logicalBackStart, unitNum - 1, diskId);
                backupDiskSpace.type = DiskSpaceType.BACKUPSPACE;
                for (int i = disk.logicalBackStart; i < unitNum; i++) {
                    disk.unitData.add(new UnitData(-1, -1, backupDiskSpace));
                }
                // RW space
                DiskSpace rwSpace = new DiskSpace(true, 0, disk.logicalRWEnd, diskId);
                for (int i = 0; i < disk.logicalBackStart; i++) {
                    disk.unitData.add(new UnitData(-1, -1, rwSpace));
                }
        }
        return disk;
    }

    public LocalDisk(int diskId, int unitNum) {
        this.diskId = diskId;
        this.unitNum = unitNum;
        this.ptr = 0;
        this.RWEnd = 0;
        this.sizeLeft = unitNum;
        this.preoper = Action.PASS;
        this.pretoken = 64;

        // 初始化集合
        // 1-5大小的空闲空间列表
        this.freespaceBySize = new HashMap<>(5);
        this.unitData = new ArrayList<>(unitNum);
    }

    public void passPtr() {
        ptr++;
    }

    // 执行操作
    public int ptrDoAction(Action action) {
        switch (action) {
            case READ:
                int objId = unitData.get(ptr).objId;
                passPtr();
                return objId;
            case PASS:
                passPtr();
                return 0;
            default:
                return -1;
        }
    }

    public int ptrDoAction(Action action, int jump) {
        switch (action) {
            case JUMP:
                ptr = jump;
                return 0;
            default:
                return -1;
        }
    }

    // 获取指定单元ID对应的空间
    public DiskSpace getSpaceForUnit(int unitId) {
        if (unitId < 0 || unitId >= unitNum)
            return null;
        return unitData.get(unitId).space;
    }

    /**
     * 执行写入时，调用该方法获取指定大小的空闲空间 方法内部会维护LocalDisk的freespaceBySize unitId
     * 
     * 
     * @param obj_size 对象大小，范围1-5
     * @return 空闲空间，用于存放对象
     */
    public DiskSpace getFreeSpaceBySize(int obj_size) {
        TreeSet<DiskSpace> spaceList = freespaceBySize.get(obj_size);
        if (spaceList.size() > 0 && spaceList.first().size == obj_size
                && spaceList.first().end <= Info.MAX_RW_END) {
            DiskSpace exactSpace = spaceList.pollFirst(); // space的大小与obj的大小恰好一致，此时不需要拆分
            exactSpace.isFree = false;

            // 维护freeSize
            sizeLeft -= obj_size;

            log.debug("恰好获取到大小相同的空闲空间: space_size = obj_size = " + obj_size + ", space信息为"
                    + exactSpace);
            return exactSpace;
        }
        // space的大小大于obj的大小，此时需要拆分space
        // 原先的space会变成两个space，一个大小为obj_size，另一个为space_size - obj_size
        for (int i = obj_size; i <= 5; i++) {
            spaceList = freespaceBySize.get(i);
            if (spaceList.size() > 0) {
                if (spaceList.first().end > Info.MAX_RW_END) {
                    if (i < 5) {
                        continue;
                    } else {
                        log.debug("实在找不到合适的空间，返回分界线后的空间");
                    }
                }
                DiskSpace spaceToCut = spaceList.pollFirst();
                log.debug("切分空间: Space的信息为: " + spaceToCut + ", 要写入的对象大小为: " + obj_size);
                DiskSpace spaceToUse = new DiskSpace(false, spaceToCut.start,
                        spaceToCut.start + obj_size - 1, diskId);
                DiskSpace spaceToRemain = new DiskSpace(true, spaceToCut.start + obj_size,
                        spaceToCut.end, diskId);
                // 更新单元到空间的映射
                for (int j = spaceToUse.start; j <= spaceToUse.end; j++) {
                    unitData.get(j).space = spaceToUse;
                }
                for (int j = spaceToRemain.start; j <= spaceToRemain.end; j++) {
                    unitData.get(j).space = spaceToRemain;
                }
                freespaceBySize.get(spaceToRemain.sizeInMap).add(spaceToRemain);
                log.debug("切分后的两个空间: spaceToUse信息为" + spaceToUse + ", spaceToRemain信息为"
                        + spaceToRemain);
                // 维护freeSize
                sizeLeft -= obj_size;
                return spaceToUse;
            }
        }
        return null;
    }

    public DiskSpace getFreeSpaceBySizeFromEnd(int obj_size) {
        TreeSet<DiskSpace> spaceList = freespaceBySize.get(obj_size);
        if (spaceList.size() > 0 && spaceList.last().size == obj_size) {
            DiskSpace exactSpace = spaceList.pollLast(); // space的大小与obj的大小恰好一致，此时不需要拆分，从后往前找
            exactSpace.isFree = false;

            log.debug("恰好获取到大小相同的空闲空间: space_size = obj_size = " + obj_size + ", space信息为"
                    + exactSpace);
            // 维护freeSize
            sizeLeft -= obj_size;
            return exactSpace;
        }
        // space的大小大于obj的大小，此时需要拆分space
        // 原先的space会变成两个space，一个大小为obj_size，另一个为space_size - obj_size
        for (int i = obj_size; i <= 5; i++) {
            spaceList = freespaceBySize.get(i);
            if (spaceList.size() > 0) {
                DiskSpace spaceToCut = spaceList.pollLast();
                log.debug("切分空间: Space的信息为: " + spaceToCut + ", 要写入的对象大小为: " + obj_size);
                DiskSpace spaceToUse = new DiskSpace(false, spaceToCut.end - obj_size + 1,
                        spaceToCut.end, diskId);
                spaceToUse.type = DiskSpaceType.RWSPACE;
                DiskSpace spaceToRemain = new DiskSpace(true, spaceToCut.start,
                        spaceToCut.end - obj_size, diskId);
                // 更新单元到空间的映射
                for (int j = spaceToUse.start; j <= spaceToUse.end; j++) {
                    unitData.get(j).space = spaceToUse;
                }
                for (int j = spaceToRemain.start; j <= spaceToRemain.end; j++) {
                    unitData.get(j).space = spaceToRemain;
                }
                freespaceBySize.get(spaceToRemain.sizeInMap).add(spaceToRemain);
                log.debug("切分后的两个空间: spaceToUse信息为" + spaceToUse + ", spaceToRemain信息为"
                        + spaceToRemain);
                // 维护freeSize
                sizeLeft -= obj_size;
                return spaceToUse;
            }
        }
        return null;
    }

    // unit ff 策略调用方法
    public ArrayList<Integer> getFreeUnitsBySize(int obj_size, int obj_id) {
        if (sizeLeft < obj_size) {
            log.debug("没有足够的空间，obj_size = " + obj_size + ", sizeLeft = " + sizeLeft);
            return null;
        }
        ArrayList<Integer> unitIdList = new ArrayList<>();

        for (int i = 0; i < obj_size; i++) {
            DiskSpace space = freespaceNotBySize.pollFirst();
            unitIdList.add(space.start);
            unitData.get(space.start).objId = obj_id;
            space.isFree = false;
        }

        // 更新RWEnd
        if (RWEnd < unitIdList.get(unitIdList.size() - 1)) {
            RWEnd = unitIdList.get(unitIdList.size() - 1);
        }
        // 更新sizeLeft
        sizeLeft -= obj_size;
        return unitIdList;
    }

    public ArrayList<Integer> getFreeUnitBySizeFromEndWithRWEndLimit(int obj_size, int obj_id) {
        if (sizeLeft < obj_size || freespaceNotBySize.size() < obj_size) {
            log.debug("没有足够的空间，obj_size = " + obj_size + ", sizeLeft = " + sizeLeft
                    + ", freespace size = " + freespaceNotBySize.size());
            return null;
        }
        ArrayList<Integer> unitIdList = new ArrayList<>();

        for (int i = 0; i < obj_size; i++) {
            DiskSpace space = freespaceNotBySize.pollLast();
            if (space == null) {
                log.debug("获取空闲空间失败，i = " + i);
                // Restore already polled spaces
                for (DiskSpace polledSpace : unitIdList.stream().map(id -> unitData.get(id).space)
                        .collect(java.util.stream.Collectors.toList())) {
                    polledSpace.isFree = true;
                    freespaceNotBySize.add(polledSpace);
                }
                return null;
            }
            unitIdList.add(space.start);
            unitData.get(space.start).objId = obj_id;
            space.isFree = false;
        }

        // 更新RWEnd
        if (RWEnd < unitIdList.get(unitIdList.size() - 1)) {
            RWEnd = unitIdList.get(unitIdList.size() - 1);
        }
        // 更新sizeLeft
        sizeLeft -= obj_size;
        return unitIdList;
    }

    /**
     * 执行写入时，调用该方法获取指定大小的空闲空间，优先考虑MAX_RW_END之后的空间 方法内部会维护LocalDisk的freespaceBySize
     * unitId，从后往前查找空间
     * 
     * @param obj_size 对象大小，范围1-5
     * @return 空闲空间，用于存放对象。优先返回MAX_RW_END之后的空间，如果没有才返回之前的空间
     */
    public DiskSpace getFreeSpaceBySizeFromEndWithRWEndLimit(int obj_size) {
        TreeSet<DiskSpace> spaceList = freespaceBySize.get(obj_size);

        // 首先尝试找到恰好大小的且在MAX_RW_END之后的空间
        if (spaceList.size() > 0) {
            for (DiskSpace space : spaceList.descendingSet()) {
                if (space.size == obj_size && space.start > Info.MAX_RW_END) {
                    spaceList.remove(space);
                    space.isFree = false;
                    sizeLeft -= obj_size;
                    log.debug("恰好获取到大小相同且在MAX_RW_END之后的空闲空间: space_size = obj_size = "
                            + obj_size + ", space信息为" + space);
                    return space;
                }
            }
        }

        // 尝试从更大的空间中找到合适的并切分（从后往前找）
        for (int i = obj_size; i <= 5; i++) {
            spaceList = freespaceBySize.get(i);
            if (spaceList.size() > 0) {
                // 先尝试找到一个合适的空间（在MAX_RW_END之后）
                DiskSpace suitableSpace = null;
                for (DiskSpace space : spaceList.descendingSet()) {
                    if (space.start > Info.MAX_RW_END) {
                        suitableSpace = space;
                        break;
                    }
                }

                // 如果找到了合适的空间
                if (suitableSpace != null) {
                    spaceList.remove(suitableSpace);
                    log.debug("切分空间: Space的信息为: " + suitableSpace + ", 要写入的对象大小为: " + obj_size);
                    DiskSpace spaceToUse = new DiskSpace(false,
                            suitableSpace.end - obj_size + 1, suitableSpace.end, diskId);
                    spaceToUse.type = DiskSpaceType.BACKUPSPACE;
                    DiskSpace spaceToRemain = new DiskSpace(true, suitableSpace.start,
                            suitableSpace.end - obj_size, diskId);
                    // 更新单元到空间的映射
                    for (int j = spaceToUse.start; j <= spaceToUse.end; j++) {
                        unitData.get(j).space = spaceToUse;
                    }
                    for (int j = spaceToRemain.start; j <= spaceToRemain.end; j++) {
                        unitData.get(j).space = spaceToRemain;
                    }
                    freespaceBySize.get(spaceToRemain.sizeInMap).add(spaceToRemain);
                    log.debug("切分后的两个空间: spaceToUse信息为" + spaceToUse + ", spaceToRemain信息为"
                            + spaceToRemain);
                    sizeLeft -= obj_size;
                    return spaceToUse;
                }
            }
        }

        // 如果在MAX_RW_END之后没有找到合适的空间，就使用最后一个可用的空间
        spaceList = freespaceBySize.get(obj_size);
        if (spaceList.size() > 0 && spaceList.last().size == obj_size) {
            DiskSpace exactSpace = spaceList.pollLast();
            exactSpace.isFree = false;
            sizeLeft -= obj_size;
            log.debug("未找到MAX_RW_END之后的空间，使用之前的空间: space_size = obj_size = " + obj_size
                    + ", space信息为" + exactSpace);
            return exactSpace;
        }

        // 最后尝试在MAX_RW_END之前的空间中切分
        for (int i = obj_size; i <= 5; i++) {
            spaceList = freespaceBySize.get(i);
            if (spaceList.size() > 0) {
                DiskSpace spaceToCut = spaceList.pollLast();
                log.debug("在MAX_RW_END之前切分空间: Space的信息为: " + spaceToCut + ", 要写入的对象大小为: "
                        + obj_size);
                DiskSpace spaceToUse = new DiskSpace(false, spaceToCut.end - obj_size + 1,
                        spaceToCut.end, diskId);
                DiskSpace spaceToRemain = new DiskSpace(true, spaceToCut.start,
                        spaceToCut.end - obj_size, diskId);
                // 更新单元到空间的映射
                for (int j = spaceToUse.start; j <= spaceToUse.end; j++) {
                    unitData.get(j).space = spaceToUse;
                }
                for (int j = spaceToRemain.start; j <= spaceToRemain.end; j++) {
                    unitData.get(j).space = spaceToRemain;
                }
                freespaceBySize.get(spaceToRemain.sizeInMap).add(spaceToRemain);
                log.debug("切分后的两个空间: spaceToUse信息为" + spaceToUse + ", spaceToRemain信息为"
                        + spaceToRemain);
                sizeLeft -= obj_size;
                return spaceToUse;
            }
        }

        return null;
    }

    // 从某位置开始，向两侧获取最近的free的space，未切割
    private DiskSpace findSpaceNearMiddle(int obj_size, int middle) {
        // 获取中心块
        log.debug("寻找距离middle最近的空闲空间: obj_size=" + obj_size + ", middle=" + middle);
        int i = middle;
        int j = middle + 1;
        while (true) {
            if (i < 0 && j >= unitNum) {
                break;
            }
            if (i >= 0 && unitData.get(i).space.isFree
                    && unitData.get(i).space.size >= obj_size) {
                log.debug("找到距离middle最近的空闲空间: space_size=" + unitData.get(i).space.size
                        + ", space_start=" + unitData.get(i).space.start + ", space_end="
                        + unitData.get(i).space.end);
                return unitData.get(i).space;
            }
            if (j < unitNum && unitData.get(j).space.isFree
                    && unitData.get(j).space.size >= obj_size) {
                log.debug("找到距离middle最近的空闲空间: space_size=" + unitData.get(j).space.size
                        + ", space_start=" + unitData.get(j).space.start + ", space_end="
                        + unitData.get(j).space.end);
                return unitData.get(j).space;
            }
            i--;
            j++;
        }
        return null;
    }

    /**
     * 获取距离middle最近空闲空间，不维护freespaceBySize
     * 
     * @param obj_size 对象大小，范围1-5
     * @param middle   中间位置
     * @return 空闲空间，用于存放对象。优先返回MAX_RW_END之后的空间，如果没有才返回之前的空间
     */
    public DiskSpace getSpaceNearMiddle(int obj_size, int middle) {
        DiskSpace space = findSpaceNearMiddle(obj_size, middle);
        if (space == null) {
            log.error("写炸了！！！！！磁盘" + diskId + "没有找到合适的空间");
            return null;
        }
        // 判断空间尺寸
        sizeLeft -= obj_size;
        if (space.size == obj_size) {
            space.isFree = false;
            space.type = DiskSpaceType.RWSPACE;
            // 维护unit信息
            for (int i = space.start; i <= space.end; i++) {
                unitData.get(i).space = space;
                unitData.get(i).objId = -1;
                unitData.get(i).blockId = -1;
            }
            log.debug("分配空间完成: space_size=" + space.size + ", space_start=" + space.start
                    + ", space_end=" + space.end);
            return space;
        }
        // 那么就是大于obj_size的，判断空间是否横跨middle
        if (space.start <= middle && space.end >= middle) {
            // 需要切割成3块
            // 1. 以middle为中心，对称切割出obj_size的空间
            int left, right;
            if (obj_size % 2 == 0) {
                // 偶数大小，左右各分一半
                left = middle - (obj_size / 2);
                right = middle + (obj_size / 2) - 1;
            } else {
                // 奇数大小，中心点归属于右边
                left = middle - (obj_size / 2);
                right = middle + (obj_size / 2);
            }
            // 判断left和right是否在space的范围内，如果不在，则需要调整
            // 且此时只有两个空间
            if (left <= space.start) {
                left = space.start;
                right = left + obj_size - 1;

                DiskSpace space2remain = new DiskSpace(true, right + 1, space.end, diskId);
                space.setStartAndEnd(left, right);
                space2remain.type = DiskSpaceType.UNUSED;
                space.isFree = false;
                space.type = DiskSpaceType.RWSPACE;
                // 维护unit信息
                for (int i = space.start; i <= space.end; i++) {
                    unitData.get(i).space = space;
                    unitData.get(i).objId = -1;
                    unitData.get(i).blockId = -1;
                }
                for (int i = space2remain.start; i <= space2remain.end; i++) {
                    unitData.get(i).space = space2remain;
                    unitData.get(i).objId = -1;
                    unitData.get(i).blockId = -1;
                }
                log.debug("disk" + diskId + "分配空间完成: space_size=" + space.size
                        + ", space_start=" + space.start + ", space_end=" + space.end);
                return space;
            }
            if (right >= space.end) {
                right = space.end;
                left = right - obj_size + 1;

                DiskSpace space2remain = new DiskSpace(true, space.start, left - 1, diskId);
                space.setStartAndEnd(left, right);
                space2remain.type = DiskSpaceType.UNUSED;
                space.isFree = false;
                space.type = DiskSpaceType.RWSPACE;
                // 维护unit信息
                for (int i = space.start; i <= space.end; i++) {
                    unitData.get(i).space = space;
                    unitData.get(i).objId = -1;
                    unitData.get(i).blockId = -1;
                }
                for (int i = space2remain.start; i <= space2remain.end; i++) {
                    unitData.get(i).space = space2remain;
                    unitData.get(i).objId = -1;
                    unitData.get(i).blockId = -1;
                }
                log.debug("disk" + diskId + "分配空间完成: space_size=" + space.size
                        + ", space_start=" + space.start + ", space_end=" + space.end);
                return space;
            }
            // 不在边缘，需要切成三块
            DiskSpace spaceLeft = new DiskSpace(true, space.start, left - 1, diskId);
            DiskSpace spaceRight = new DiskSpace(true, right + 1, space.end, diskId);
            spaceLeft.type = DiskSpaceType.UNUSED;
            spaceRight.type = DiskSpaceType.UNUSED;
            space.setStartAndEnd(left, right);
            space.isFree = false;
            space.type = DiskSpaceType.RWSPACE;
            // 维护unit信息
            for (int i = space.start; i <= space.end; i++) {
                unitData.get(i).space = space;
                unitData.get(i).objId = -1;
                unitData.get(i).blockId = -1;
            }
            for (int i = spaceLeft.start; i <= spaceLeft.end; i++) {
                unitData.get(i).space = spaceLeft;
                unitData.get(i).objId = -1;
                unitData.get(i).blockId = -1;
            }
            for (int i = spaceRight.start; i <= spaceRight.end; i++) {
                unitData.get(i).space = spaceRight;
                unitData.get(i).objId = -1;
                unitData.get(i).blockId = -1;
            }
            log.debug("disk" + diskId + "分配空间完成: space_size=" + space.size + ", space_start="
                    + space.start + ", space_end=" + space.end);
            return space;
        }
        // 那么就是不横跨middle，从边缘开始切割
        DiskSpace space2remain = null;
        int start = 0;
        int end = 0;
        if (space.end <= middle) {
            // 从end开始切割
            end = space.end;
            start = end - obj_size + 1;

            space2remain = new DiskSpace(true, space.start, start - 1, diskId);
            space.setStartAndEnd(start, end);
        } else {
            // 从start开始切割
            start = space.start;
            end = start + obj_size - 1;

            space2remain = new DiskSpace(true, end + 1, space.end, diskId);
            space.setStartAndEnd(start, end);
        }
        space2remain.type = DiskSpaceType.UNUSED;
        space.isFree = false;
        space.type = DiskSpaceType.RWSPACE;
        // 维护unit信息
        for (int i = space.start; i <= space.end; i++) {
            unitData.get(i).space = space;
            unitData.get(i).objId = -1;
            unitData.get(i).blockId = -1;
        }
        for (int i = space2remain.start; i <= space2remain.end; i++) {
            unitData.get(i).space = space2remain;
            unitData.get(i).objId = -1;
            unitData.get(i).blockId = -1;
        }
        log.debug("disk" + diskId + "分配空间完成: space_size=" + space.size + ", space_start="
                + space.start + ", space_end=" + space.end);
        return space;
    }

    /**
     * 执行删除后，调用该方法维护LocalDisk的freespaceBySize。
     * 同时更新unitToSpace。
     * 时间复杂度 O(n)
     * 维护的信息有
     * 1. localdisk的rwEnd
     * 2. unitData的objId和blockId
     * 3. freespaceBySize
     * 
     * @param space 要释放的DiskSpace对象
     */
    public void releaseSpace(DiskSpace space) {
        log.debug("释放空间: " + space);
        if (space.diskId != diskId) {
            log.error("释放空间: " + space + " 不是本磁盘，异常");
            return;// 不是本磁盘，异常报错
        }

        // isFree = true
        int lastSize = space.sizeInMap;
        space.isFree = true;
        space.type = DiskSpaceType.UNUSED;
        // 更新rwend
        if (space.end == RWEnd) {
            updateRWEndAfterRelease(space);
        }
        // 合并前后空间
        DiskSpace prevSpace = space.start > 0 ? unitData.get(space.start - 1).space : null;
        DiskSpace nextSpace = space.end < unitNum - 1 ? unitData.get(space.end + 1).space : null;
        if (prevSpace != null && prevSpace.isFree) {
            // log.debug("合并前空间: " + prevSpace);
            space.setStartAndEnd(prevSpace.start, space.end);
            freespaceBySize.get(prevSpace.sizeInMap).remove(prevSpace);
        }
        if (nextSpace != null && nextSpace.isFree) {
            // log.debug("合并后空间: " + nextSpace);
            space.setStartAndEnd(space.start, nextSpace.end);
            freespaceBySize.get(nextSpace.sizeInMap).remove(nextSpace);
        }
        // 更新单元到空间的映射
        for (int i = space.start; i <= space.end; i++) {
            unitData.get(i).space = space;
            unitData.get(i).objId = -1;
            unitData.get(i).blockId = -1;
        }
        // 更新按大小组织的集合
        log.debug("释放完成: " + space.toString());
        freespaceBySize.get(lastSize).remove(space);
        freespaceBySize.get(space.sizeInMap).add(space);
    }

    /**
     * 作为releaseSpace的辅助方法
     * 当release的space恰好是RWSpace的最后一个空间，调用该方法更新RWEnd；
     * 
     * @param space 释放的space，它恰好是RWSpace的最后一个空间
     */
    private void updateRWEndAfterRelease(DiskSpace space) {
        int prevUnitIndex = space.start - 1;
        DiskSpace prevDiskSpace = space.start > 0 ? unitData.get(prevUnitIndex).space : null;
        while (prevDiskSpace != null && prevDiskSpace.isFree) {
            prevUnitIndex = prevDiskSpace.start - 1;
            prevDiskSpace = prevUnitIndex >= 0 ? unitData.get(prevUnitIndex).space : null;
        }
        log.debug("释放space后更新RWEnd: " + RWEnd + " -> " + prevUnitIndex);
        RWEnd = prevUnitIndex;
    }
}