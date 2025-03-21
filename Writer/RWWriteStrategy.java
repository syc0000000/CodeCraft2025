package Writer;

import java.util.ArrayList;
import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.UserObject;
import Info.Info.DiskSpace;
import Info.Info.Replica;
import IO.model.DiskUnit;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class RWWriteStrategy extends DefaultWriteStrategy {
    private final ModuleLogger log = LoggerFactory.getLogger("Writer");

    @Override
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        ArrayList<WriteCommandOut> writeCommandOuts = new ArrayList<>();
        for (WriteCommandIn writeCommandIn : writeCommandIns) {
            WriteCommandOut writeCommandOut = new WriteCommandOut();
            writeCommandOut.objId = writeCommandIn.objId;
            UserObject obj = new UserObject(writeCommandIn.objId, writeCommandIn.size, writeCommandIn.tag);
            Info.objMap.put(writeCommandIn.objId, obj);
            ArrayList<LocalDisk> disks = selectDiskBySizeLeft();
            // 处理RW磁盘
            LocalDisk rwDisk = disks.get(0);
            DiskSpace space = rwDisk.getFreeSpaceBySize(obj.objSize);
            // 找到写入空间
            if (space != null) {
                ArrayList<Integer> unitIdList = new ArrayList<>();
                for (int j = 0; j < space.size; j++) {
                    unitIdList.add(space.start + j);
                }
                // 分配空间
                Replica replica = new Replica(writeCommandIn.objId, 0, rwDisk.diskId, unitIdList);
                // space.replica = replica;
                addReplicaToObj(obj, replica);
                saveReplicaToDisk(rwDisk, replica);
                // 维护RWEnd
                rwDisk.RWEnd = Math.min(Info.MAX_RW_END, Math.max(rwDisk.RWEnd, space.end));
                log.debug("成功写入副本0到磁盘" + rwDisk.diskId);
                writeCommandOut.copy1 = new DiskUnit(rwDisk.diskId, unitIdList);
            }
            // 处理Backup磁盘
            for (int i = 1; i < disks.size(); i++) {
                LocalDisk backupDisk = disks.get(i);
                space = backupDisk.getFreeSpaceBySizeFromEnd(obj.objSize);
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
     * 选择3个磁盘，[0]作为RW磁盘，[1,2]作为Backup磁盘。
     * 
     * @param size
     * @return
     */
    private ArrayList<LocalDisk> selectDiskBySizeLeft() {
        ArrayList<LocalDisk> disks = new ArrayList<>(3);
        // 使用三个变量记录最大的三个磁盘，[0]作为RW磁盘，[1,2]作为Backup磁盘。
        // O(n)实现，虽然看起来有点丑
        LocalDisk max1 = null, max2 = null, max3 = null;
        int size1 = Integer.MIN_VALUE, size2 = Integer.MIN_VALUE, size3 = Integer.MIN_VALUE;

        // 一次遍历找出最大的三个磁盘
        for (LocalDisk disk : Info.localDiskTbl) {
            if (disk.sizeLeft > size1) {
                max3 = max2;
                size3 = size2;
                max2 = max1;
                size2 = size1;
                max1 = disk;
                size1 = disk.sizeLeft;
            } else if (disk.sizeLeft > size2) {
                max3 = max2;
                size3 = size2;
                max2 = disk;
                size2 = disk.sizeLeft;
            } else if (disk.sizeLeft > size3) {
                max3 = disk;
                size3 = disk.sizeLeft;
            }
        }

        // 按顺序添加到结果列表中
        if (max1 != null)
            disks.add(max1);
        if (max2 != null)
            disks.add(max2);
        if (max3 != null)
            disks.add(max3);

        return disks;
    }
}
