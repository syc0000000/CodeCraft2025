package Reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import Info.Info;
import Info.Info.LocalDisk;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import IO.model.CompleteCommandOut;

public class SycReaderStrategy implements ReaderStrategy {
    private static final int[] R_COSTS = { 64, 52, 42, 34, 28, 23, 19, 16 };

    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        // 处理Task
        addReadTask(readCommandIns);
        ReadRetrun readRetrun = new ReadRetrun();
        int tickToken = Info.tokenPerTick;
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();
        // 遍历磁盘
        for (int i = 0; i < Info.diskNum; i++) {
            LocalDisk disk = Info.localDiskTbl.get(i);
        }
        return readRetrun;
    }
}
