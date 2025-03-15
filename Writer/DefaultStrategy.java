package Writer;

import java.util.ArrayList;

import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.Info.Replica;
import Info.Info.LocalDisk;
import Info.Info.UserObject;
import Info.Info.DiskSpace;

public class DefaultStrategy implements WriteStrategy {
    @Override
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        // TODO: 实现默认的写策略
        return null;
    }

    private void addReplicaToObj(UserObject obj, Replica replica) {
        obj.addReplica(replica);
    }

    private DiskSpace saveReplicaToDisk(LocalDisk disk, Replica replica) {
        DiskSpace space = disk.getFreeSpaceBySize(replica.unitIdList.size());
        if (space == null) {
            throw new RuntimeException("No free space found for replica " + replica.objId);
        }
        for (int unitId : replica.unitIdList) {
            disk.unitData[unitId] = replica.objId;
        }
        return space;
    }
}
