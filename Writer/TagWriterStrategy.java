package Writer;

import java.util.ArrayList;

import IO.model.DiskUnit;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.Info;
import Info.Info.DiskSpace;
import Info.Info.LocalDisk;
import Info.Info.Replica;
import Info.Info.Tag;
import Info.Info.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class TagWriterStrategy implements WriteStrategy {
    private final ModuleLogger log = LoggerFactory.getLogger("Writer");

    @Override
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        ArrayList<WriteCommandOut> writeCommandOuts = new ArrayList<>();
        for (WriteCommandIn writeCommandIn : writeCommandIns) {
            WriteCommandOut writeCommandOut = new WriteCommandOut();
            writeCommandOut.objId = writeCommandIn.objId;
            UserObject obj = new UserObject(writeCommandIn.objId, writeCommandIn.size, writeCommandIn.tag);
            Info.objMap.put(writeCommandIn.objId, obj);
            // Get disks based on tag information
            Tag tag = Info.tags.get(writeCommandIn.tag);
            ArrayList<LocalDisk> disk = selectDiskByTag(tag.tagId);

            if (disk == null || disk.size() < 3) {
                log.error("无法为对象" + writeCommandIn.objId + "找到足够的磁盘");
                continue;
            }

            // rw disk
            LocalDisk rwDisk = disk.get(0);
            DiskSpace space = rwDisk.findSpaceNearMiddle(obj.objSize, tag.middleList.get(rwDisk.diskId));
            if (space != null) {
                ArrayList<Integer> unitIdList = new ArrayList<>();
                for (int j = 0; j < space.size; j++) {
                    unitIdList.add(space.start + j);
                }
                // 分配空间
                Replica replica = new Replica(writeCommandIn.objId, 0, rwDisk.diskId, unitIdList);
                addReplicaToObj(obj, replica);
                saveReplicaToDisk(rwDisk, replica);
                // 维护RWEnd
                rwDisk.RWEnd = Math.min(Info.MAX_RW_END, Math.max(rwDisk.RWEnd, space.end));
                log.debug("成功写入副本0到磁盘" + rwDisk.diskId);
                writeCommandOut.copy1 = new DiskUnit(rwDisk.diskId, unitIdList);
            }
            // 处理Backup磁盘, same as RWWriteStrategy
            for (int i = 1; i < disks.size(); i++) {
                LocalDisk backupDisk = disks.get(i);
                space = backupDisk.getFreeSpaceBySizeFromEndWithRWEndLimit(obj.objSize);
                if (space != null) {
                    ArrayList<Integer> unitIdList = new ArrayList<>();
                    for (int j = 0; j < space.size; j++) {
                        unitIdList.add(space.start + j);
                    }
                    // 分配空间
                    Replica replica = new Replica(writeCommandIn.objId, i, backupDisk.diskId, unitIdList);
                    // space.replica = replica;
                    addReplicaToObj(obj, replica);
                    saveReplicaToDisk(backupDisk, replica);
                    log.debug("成功写入副本" + i + "到磁盘" + backupDisk.diskId);
                    if (i == 1) {
                        writeCommandOut.copy2 = new DiskUnit(backupDisk.diskId, unitIdList);
                    } else if (i == 2) {
                        writeCommandOut.copy3 = new DiskUnit(backupDisk.diskId, unitIdList);
                    }
                }
            }
            writeCommandOuts.add(writeCommandOut);
        }
        return writeCommandOuts;
    }
    
    /**
     * 基于给定的标签ID和对象大小选择磁盘。
     * 选择一个与标签关联的读写磁盘，以及两个具有最大可用空间的备份磁盘。
     * 1. 函数内部更换tag对应的disk的sizeList
     *
     * @param tagId   标签的ID。
     * @param objSize 要写入的对象的大小。
     * @return 一个包含所选 LocalDisk 对象的 ArrayList。
     *         列表中的第一个磁盘是读写磁盘，后跟两个备份磁盘（如果可用）。
     */
    private ArrayList<LocalDisk> selectDiskByTag(int tagId, int objSize) {
        ArrayList<LocalDisk> candidateDisks = new ArrayList<>();

        // select disks based on tag information
        Tag tag = Info.tags.get(tagId);
        int rwDiskId = tag.getDiskId();
        tag.sizeList.get(rwDiskId) -= objSize;
        LocalDisk rwDisk = Info.disks.get(rwDiskId);

        // 选两个磁盘放对象的backup replica，优先选择sizeLeft最大的2个磁盘
        LocalDisk backupDisk1 = null, backupDisk2 = null;
        int size1 = Integer.MIN_VALUE, size2 = Integer.MIN_VALUE;
        for (LocalDisk disk : Info.disks) {
            if (disk.diskId == rwDiskId) {
                continue;
            }
            int sizeLeft = disk.sizeLeft;
            if (sizeLeft > size1) {
                size2 = size1;
                size1 = sizeLeft;
                backupDisk2 = backupDisk1;
                backupDisk1 = disk;
            } else if (sizeLeft > size2) {
                size2 = sizeLeft;
                backupDisk2 = disk;
            }
        }

        candidateDisks.add(rwDisk);

        // 添加两个备份磁盘
        if (backupDisk1 != null) {
            candidateDisks.add(backupDisk1);
        }
        if (backupDisk2 != null) {
            candidateDisks.add(backupDisk2);
        }

        return candidateDisks;
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
}
