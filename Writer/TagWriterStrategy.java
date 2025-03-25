package Writer;

import java.util.ArrayList;

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
            ArrayList<LocalDisk> disk = selectDiskByTag(writeCommandIn.tag);

            if (disk == null || disk.size() < 3) {
                log.error("无法为对象" + writeCommandIn.objId + "找到足够的磁盘");
                continue;
            }

            // rw disk
            LocalDisk rwDisk = disk.get(0);
            


        }
        return writeCommandOuts;
    }

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
}
