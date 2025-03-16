package Reader;

import java.util.ArrayList;

import IO.model.ReadCommandIn;
import IO.model.ReadRetrun;
import Info.Info;
import Info.Info.LocalDisk;

public interface ReaderStrategy {
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns);

    // 计算操作消耗的token，提供默认实现
    public default int calculateToken(Info.Action action, LocalDisk disk) {
        switch (action) {
            case READ:
                // 四舍五入
                int token = (int) Math.round(disk.pretoken * 0.8);
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
