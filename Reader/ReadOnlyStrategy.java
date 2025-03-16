package Reader;

import java.util.ArrayList;

import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import Info.Info;
import Info.Info.LocalDisk;

public class ReadOnlyStrategy implements ReaderStrategy {
    @Override
    public ArrayList<ReadCommandOut> read(ArrayList<ReadCommandIn> readCommandIns) {
        // 每TickToken
        int tickToken = Info.tokenPerTick;
        ArrayList<ReadCommandOut> readCommandOuts = new ArrayList<>();
        // 遍历磁盘
        for (int i = 0; i < Info.diskNum; i++) {
            LocalDisk disk = Info.localDiskTbl.get(i);
            ReadCommandOut readCommandOut = new ReadCommandOut();
            readCommandOut.actions = new ArrayList<>();
            int tokenNow = tickToken;
            if (disk != null) {
                while (true) {
                    // 计算读取操作消耗的token
                    int token = calculateToken(Info.Action.READ, disk);
                    if (token > tokenNow) {
                        break;
                    }
                    tokenNow -= token;
                    disk.pretoken = token;
                    disk.preoper = Info.Action.READ;
                    disk.ptrDoAction(Info.Action.READ);
                    readCommandOut.actions.add(Info.Action.READ);
                }
            }
            readCommandOuts.add(i, readCommandOut);
        }
        return readCommandOuts;
    }
}
