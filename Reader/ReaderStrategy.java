package Reader;

import java.util.ArrayList;
import java.util.HashSet;

import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.ReadTask;
import Info.Info.Replica;
import Info.Info.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public interface ReaderStrategy {
    // public static final Logger logger = LoggerFactory.getLogger("Reader");
    public static final ModuleLogger readerLogger = LoggerFactory.getLogger("Reader");

    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns);

    // 添加任务
    public default void addReadTask(ArrayList<ReadCommandIn> readCommandIns) {
        for (ReadCommandIn readCommandIn : readCommandIns) {
            UserObject object = Info.objMap.get(readCommandIn.objId);
            ReadTask readTask = new ReadTask(readCommandIn.commandId, readCommandIn.objId, object.objSize);
            object.readTasks.add(readTask);
            for(int i = 0; i < 3; i++){
                //找到对应的副本
                Replica replica = object.replicas.get(i);
                int diskId = replica.diskId;
                ArrayList<Integer> unitIDList = replica.unitIdList;
                for(int j = 0; j < object.objSize; j++){
                    //设置单元中的isInTask为true
                    Info.localDiskTbl.get(diskId).unitData.get(unitIDList.get(j)).isInTask = true; 
                }
                
            }
            //readerLogger.debug("加入objTaskMap: " + readCommandIn.commandId + " " +
            //        readCommandIn.objId + "当前任务");
        }
    }
    
    // 对读写进行限制边界，在tick开始时进行检测，是否超越了这个边界，如果超越边界则跳跃回起始
    public default void restrictRangeInDisk(LocalDisk disk, int start, int end, ReadCommandOut readCommandOut) {
        if (disk.ptr > end) {
            disk.ptr = start;
            readCommandOut.actions.add(Info.Action.JUMP);
            readCommandOut.jumpTarget = start;
            disk.pretoken = 64;
            disk.preoper = Info.Action.JUMP;
        }
    }

    // 计算操作消耗的token，提供默认实现
    public default int calculateToken(Info.Action action, LocalDisk disk) {
        switch (action) {
            case READ:
                // 向上取整
                // readerLogger.debug("计算token: pretoken=" + disk.pretoken);
                int token;
                if (disk.preoper == action.READ) {
                    token = (int) Math.ceil(disk.pretoken * 0.8);
                } else {
                    token = 64;
                }
                return token < 16 ? 16 : token;
            case JUMP:
                return Info.tokenPerTick;
            case PASS:
                return 1;
            default:
                return -1;// 异常
        }
    }
    /**
     * @brief 根据硬盘和action，执行操作，将操作记录在readCmmandout中。只允许read和pass操作
     * @param action
     * @param disk
     * @param readCommandOut
     */
    public default void processAction(Info.Action action, LocalDisk disk, ReadCommandOut readCommandOut){
        readCommandOut.actions.add(action);
        disk.pretoken = calculateToken(action, disk);
        disk.preoper = action;
        disk.ptrDoAction(action);
    }
}
