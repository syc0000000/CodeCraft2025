package Writer;

import java.util.ArrayList;

import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.Info.Replica;
import Info.Info.LocalDisk;
import Info.Info.UserObject;
import Info.Info.DiskSpace;

public class DefaultWriteStrategy implements WriteStrategy {
    @Override
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        // TODO: 实现默认的写策略
        for (WriteCommandIn writeCommandIn : writeCommandIns) {
            UserObject obj = new UserObject(writeCommandIn.objId, writeCommandIn.size, writeCommandIn.tag);
            // 选3块磁盘
            ArrayList<LocalDisk> disks = selectDisk(obj);
            for (int i = 0; i < 3; i++) {
                LocalDisk disk = disks.get(i);
                // 找一片空间
                DiskSpace space = disk.getFreeSpaceBySize(obj.objSize);
                if (space != null) {
                    ArrayList<Integer> unitIdList = new ArrayList<>();
                    for (int j = 0; j < space.size; j++) {
                        unitIdList.add(space.start + j);
                    }
                    // 分配空间
                    Replica replica = new Replica(writeCommandIn.objId, i, disk.diskId, unitIdList);
                    space.replica = replica;
                    addReplicaToObj(obj, replica);
                    saveReplicaToDisk(disk, replica);
                }
            }
        }
        return null;
    }

    private void addReplicaToObj(UserObject obj, Replica replica) {
        obj.addReplica(replica);
    }

    private void saveReplicaToDisk(LocalDisk disk, Replica replica) {
        for (int unitId : replica.unitIdList) {
            disk.unitData[unitId] = replica.objId;
        }
    }

    private ArrayList<LocalDisk> selectDisk(UserObject obj) {
        // 选择三个磁盘
        int[] initialDisks = new int[3];
        initialDisks[0] = ((obj.objId - 1 + Info.Info.diskNum) % Info.Info.diskNum);
        initialDisks[1] = (obj.objId % Info.Info.diskNum);
        initialDisks[2] = ((obj.objId + 1) % Info.Info.diskNum);

        // 返回ArrayList<LocalDisk>
        ArrayList<LocalDisk> disks = new ArrayList<>();
        for (int diskId : initialDisks) {
            LocalDisk disk = Info.Info.localDiskTbl.get(diskId);
            if (disk != null) {
                disks.add(disk);
            }
        }
        return disks;
    }
}
