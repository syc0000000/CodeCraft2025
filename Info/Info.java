package Info;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import IO.model.*;

// Info模块 - 管理全局信息和数据结构
public class Info {
    // 计数器
    public static int diskNum; // 硬盘数量
    public static int unitNum; // 存储单元数量
    public static int commandNum; // 命令总数
    public static int readNum; // 读命令数
    public static int writeNum; // 写命令数
    public static int deleteNum; // 删除命令数

    // 全局参数 (对应C++全局变量)
    public static int objNums; // 已经存储的对象数量
    public static int timestamp; // 当前时间戳
    public static int tickNums; // 总tick数
    public static int tokenPerTick; // 每tick令牌数
    public static int tagNums; // 标签数量

    // 数据结构映射
    public static Map<Integer, UserObject> objMap = new HashMap<>(); // 对象id和对象的映射
    public static List<LocalDisk> localDiskTbl = new ArrayList<>(); // 本地磁盘信息
    public static Map<Integer, Set<Integer>> objTaskMap = new HashMap<>(); // 对象id和任务id的映射
    public static Map<Integer, ReadTask> readTaskTbl = new HashMap<>(); // taskid和实体的映射

    // 初始化Info模块
    public static void init() {
        // 重置计数器
        diskNum = 0;
        unitNum = 0;
        commandNum = 0;
        readNum = 0;
        writeNum = 0;
        deleteNum = 0;

        // 重置全局参数
        objNums = 0;
        timestamp = 0;

        // 清空映射
        objMap.clear();
        localDiskTbl.clear();
        objTaskMap.clear();
        readTaskTbl.clear();
    }

    // 根据预处理结果初始化系统参数
    public static void initFromPreprocessOut(PreprocessOut preOut) {
        tickNums = preOut.T; // 总tick数
        tagNums = preOut.M; // 标签总数
        diskNum = preOut.N; // 硬盘个数
        unitNum = preOut.V; // 每个硬盘存储单元数
        tokenPerTick = preOut.G; // 每tick Token数

        // 初始化磁盘表
        for (int i = 0; i < diskNum; i++) {
            localDiskTbl.add(new LocalDisk(i, unitNum));
        }
    }

    // 副本类
    public static class Replica {
        public int objId; // 对象id
        public int replicaId; // 副本id
        public int diskId; // 磁盘id
        public ArrayList<Integer> unitIdList; // unit id列表

        public Replica(int objId, int replicaId, int diskId, ArrayList<Integer> unitIdList) {
            this.objId = objId;
            this.replicaId = replicaId;
            this.diskId = diskId;
            this.unitIdList = unitIdList;
        }
    }

    // 对象类 - 存储对象信息
    public static class UserObject {
        public int objId; // 对象id
        public int objSize; // 对象大小
        public int objTag; // 对象标签
        public ArrayList<Replica> replicas; // 副本ID到Replica的映射

        public UserObject(int objId, int objSize, int objTag) {
            this.objId = objId;
            this.objSize = objSize;
            this.objTag = objTag;
            this.replicas = new ArrayList<>(3);
        }

        public UserObject(int objId, int objSize, int objTag, ArrayList<Replica> replicas) {
            this.objId = objId;
            this.objSize = objSize;
            this.objTag = objTag;
            this.replicas = replicas;
        }

        public void addReplica(Replica replica) {
            replicas.add(replica.replicaId, replica);
        }
    }

    // 磁盘空间类 - 表示空闲或占用的空间
    public static class DiskSpace {
        public boolean isFree; // true:空闲，false:占用
        public int start; // 空间起点
        public int end; // 空间终点
        public int size; // 空间大小(缓存以避免重复计算)
        public int sizeInMap; // 空间大小(用于Map的key)
        public int diskId; // 所属磁盘ID

        public DiskSpace(boolean isFree, int start, int end, int diskId) {
            this.isFree = isFree;
            this.start = start;
            this.end = end;
            this.size = end - start + 1;
            this.diskId = diskId;
            this.sizeInMap = size > 5 ? 5 : size;
        }

        @Override
        public String toString() {
            return "DiskSpace[disk=" + diskId + ", start=" + start + ", end=" + end +
                    ", size=" + size + ", free=" + isFree + "]";
        }

        public void setStartAndEnd(int start, int end) {
            this.start = start;
            this.end = end;
            this.size = end - start + 1;
            this.sizeInMap = size > 5 ? 5 : size;
        }
    }

    public enum Action {
        READ, PASS, JUMP
    }

    // 本地磁盘类 - 存储磁盘信息
    public static class LocalDisk {
        public int diskId; // 磁盘id
        public int unitNum; // 存储单元数量
        public int ptr; // 当前磁头指针位置
        public Action preoper; // 上一次操作
        public int pretoken; // 上一次令牌数量

        // 优化: 按大小组织空闲空间的集合
        // key: 空间大小1-5, value: 该大小的空闲空间列表
        public HashMap<Integer, LinkedList<DiskSpace>> freespaceBySize;

        // 存储单元数据（对象ID, -1表示空）
        public int[] unitData;

        // 单元ID到空间的映射
        public Map<Integer, DiskSpace> unitToSpace;

        public LocalDisk(int diskId, int unitNum) {
            this.diskId = diskId;
            this.unitNum = unitNum;
            this.ptr = 0;
            this.preoper = Action.PASS;
            this.pretoken = 0;

            // 初始化集合
            // 1-5大小的空闲空间列表
            this.freespaceBySize = new HashMap<>(5);
            this.unitData = new int[unitNum];
            this.unitToSpace = new HashMap<>(unitNum);

            // 初始化存储单元数据
            for (int i = 0; i < unitNum; i++) {
                unitData[i] = -1; // -1表示空
            }

            // 创建初始空闲空间
            DiskSpace initialSpace = new DiskSpace(true, 0, unitNum - 1, diskId);

            // 添加到按大小组织的集合
            freespaceBySize.put(1, new LinkedList<>());
            freespaceBySize.put(2, new LinkedList<>());
            freespaceBySize.put(3, new LinkedList<>());
            freespaceBySize.put(4, new LinkedList<>());
            freespaceBySize.put(5, new LinkedList<>());

            freespaceBySize.get(5).add(initialSpace);

            // 更新单元到空间的映射
            for (int i = 0; i < unitNum; i++) {
                unitToSpace.put(i, initialSpace);
            }
        }

        // 获取指定单元ID对应的空间
        public DiskSpace getSpaceForUnit(int unitId) {
            if (unitId < 0 || unitId >= unitNum)
                return null;
            return unitToSpace.get(unitId);
        }

        // 获取指定大小的空闲空间
        public DiskSpace getFreeSpaceBySize(int size) {
            DiskSpace space = freespaceBySize.get(size).getFirst();
            if (space != null) {
                return space;
            }
            for (int i = size + 1; i <= 5; i++) {
                space = freespaceBySize.get(i).getFirst();
                if (space != null) {
                    // 切割空间
                    getFreeSpaceByCut(space, size);
                    return space;
                }
            }
            return null;
        }

        // 释放指定空间
        public void releaseSpace(DiskSpace space) {
            if (space.diskId != diskId)
                return;// 不是本磁盘，异常报错

            // isFree = true
            space.isFree = true;
            // 合并前后空间
            DiskSpace prevSpace = unitToSpace.get(space.start - 1);
            DiskSpace nextSpace = unitToSpace.get(space.end + 1);
            if (prevSpace != null && prevSpace.isFree) {
                space.setStartAndEnd(prevSpace.start, space.end);
                freespaceBySize.get(prevSpace.sizeInMap).remove(prevSpace);
            }
            if (nextSpace != null && nextSpace.isFree) {
                space.setStartAndEnd(space.start, nextSpace.end);
                freespaceBySize.get(nextSpace.sizeInMap).remove(nextSpace);
            }
            // 更新单元到空间的映射
            for (int i = space.start; i <= space.end; i++) {
                unitToSpace.put(i, space);
            }
            // 更新按大小组织的集合
            freespaceBySize.get(space.sizeInMap).addFirst(space);
        }

        // 切割空间
        public void getFreeSpaceByCut(DiskSpace space, int size) {
            int lastSize = space.size;
            int lastEnd = space.end;
            int lastStart = space.start;
            // 缩小原有空间
            space.setStartAndEnd(lastStart, lastStart + size - 1);
            // 创建新空间
            DiskSpace newSpace = new DiskSpace(true, space.end + 1, lastEnd, diskId);
            freespaceBySize.get(newSpace.sizeInMap).add(newSpace);
            for (int i = newSpace.start; i <= newSpace.end; i++) {
                unitToSpace.put(i, newSpace);
            }
            // 删除原有空间
            freespaceBySize.get(lastSize).remove(space);
        }
    }

    // 读任务类 - 存储读任务信息
    public static class ReadTask {
        // 任务属性
        public int taskId; // 任务id
        public int startTime; // 任务已经占用的时间
        public int taskValue; // 任务价值

        // 任务内容
        public int objId; // 对象id
        public int objSize; // 对象大小
        public int objTag; // 对象标签
        public Set<Integer> blockFinished; // 已经完成的块
        public Set<Integer> blockNotFinished; // 未完成的块

        public ReadTask(int taskId, int objId) {
            this.taskId = taskId;
            this.objId = objId;
            this.startTime = 0;
            this.taskValue = 0;
            this.blockFinished = new HashSet<>();
            this.blockNotFinished = new HashSet<>();
        }
    }
}
