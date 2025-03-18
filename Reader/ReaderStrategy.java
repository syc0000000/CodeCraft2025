package Reader;

import java.util.ArrayList;
import java.util.HashSet;

import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.ReadTask;
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
            readerLogger.debug("加入objTaskMap: " + readCommandIn.commandId + " " +
                    readCommandIn.objId + "当前任务" + object.readTasks);
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
}
