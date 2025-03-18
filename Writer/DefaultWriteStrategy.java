package Writer;

import java.util.ArrayList;
import java.util.stream.Collectors;

import IO.model.DiskUnit;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.Info;
import Info.Info.Replica;
import Info.Info.LocalDisk;
import Info.Info.UserObject;
import Info.Info.DiskSpace;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class DefaultWriteStrategy implements WriteStrategy {
    private final ModuleLogger log = LoggerFactory.getLogger("Writer");

    @Override
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        ArrayList<WriteCommandOut> writeCommandOuts = new ArrayList<>();
        for (WriteCommandIn writeCommandIn : writeCommandIns) {
            log.info("开始处理写入命令: objId=" + writeCommandIn.objId + ", size=" + writeCommandIn.size + ", tag="
                    + writeCommandIn.tag);

            WriteCommandOut writeCommandOut = new WriteCommandOut();
            writeCommandOut.objId = writeCommandIn.objId;
            UserObject obj = new UserObject(writeCommandIn.objId, writeCommandIn.size, writeCommandIn.tag);
            Info.objMap.put(writeCommandIn.objId, obj);

            // 选3块磁盘
            ArrayList<LocalDisk> disks = selectDisk(obj);
            log.debug("已选择磁盘: " + disks.stream().map(disk -> disk.diskId).collect(Collectors.toList()));

            for (int i = 0; i < 3; i++) {
                LocalDisk disk = disks.get(i);
                log.debug("正在处理磁盘" + disk.diskId + "的写入");

                // 找一片空间
                DiskSpace space = disk.getFreeSpaceBySize(obj.objSize);
                if (space != null) {
                    log.debug("在磁盘" + disk.diskId + "上找到可用空间: start=" + space.start + ", size=" + space.size);

                    ArrayList<Integer> unitIdList = new ArrayList<>();
                    for (int j = 0; j < space.size; j++) {
                        unitIdList.add(space.start + j);
                    }
                    // 分配空间
                    Replica replica = new Replica(writeCommandIn.objId, i, disk.diskId, unitIdList);
                    // space.replica = replica;
                    addReplicaToObj(obj, replica);
                    saveReplicaToDisk(disk, replica);

                    log.debug("成功写入副本" + i + "到磁盘" + disk.diskId);

                    if (i == 0) {
                        writeCommandOut.copy1 = new DiskUnit(disk.diskId, unitIdList);
                    } else if (i == 1) {
                        writeCommandOut.copy2 = new DiskUnit(disk.diskId, unitIdList);
                    } else if (i == 2) {
                        writeCommandOut.copy3 = new DiskUnit(disk.diskId, unitIdList);
                    }
                } else {
                    log.warn("在磁盘" + disk.diskId + "上未找到足够的可用空间");
                }
            }
            writeCommandOuts.add(writeCommandOut);
            log.info("完成写入命令处理: objId=" + writeCommandIn.objId);
        }
        return writeCommandOuts;
    }

    protected void addReplicaToObj(UserObject obj, Replica replica) {
        log.debug("添加副本到对象: objId=" + obj.objId + ", replicaId=" + replica.replicaId);
        obj.addReplica(replica);
    }

    protected void saveReplicaToDisk(LocalDisk disk, Replica replica) {
        log.debug("保存副本到磁盘: diskId=" + disk.diskId + ", objId=" + replica.objId);
        for (int i = 0; i < replica.unitIdList.size(); i++) {
            disk.unitData.get(replica.unitIdList.get(i)).objId = replica.objId;
            disk.unitData.get(replica.unitIdList.get(i)).blockId = i;
        }
    }

    private ArrayList<LocalDisk> selectDisk(UserObject obj) {
        // 选择三个磁盘
        int[] initialDisks = new int[3];
        initialDisks[0] = ((obj.objId - 1 + Info.diskNum) % Info.diskNum);
        initialDisks[1] = (obj.objId % Info.diskNum);
        initialDisks[2] = ((obj.objId + 1) % Info.diskNum);

        log.debug("为对象" + obj.objId + "选择磁盘: " + initialDisks[0] + ", " + initialDisks[1] + ", " + initialDisks[2]);

        // 返回ArrayList<LocalDisk>
        ArrayList<LocalDisk> disks = new ArrayList<>();
        for (int diskId : initialDisks) {
            LocalDisk disk = Info.localDiskTbl.get(diskId);
            if (disk != null) {
                disks.add(disk);
            } else {
                log.error("未找到磁盘: diskId=" + diskId);
            }
        }
        return disks;
    }

}
