package Reader;

import java.util.ArrayList;

import IO.model.ReadCommandIn;
import IO.model.ReadRetrun;
import Info.Info;
import Info.Info.LocalDisk;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public interface ReaderStrategy {
    // public static final Logger logger = LoggerFactory.getLogger("Reader");
    public static final ModuleLogger readerLogger = LoggerFactory.getLogger("Reader");

    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns);

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
