package Writer;

import java.util.ArrayList;
import Info.Info;
import Info.Info.DiskSpace;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.Info.UserObject;
import Info.Info.LocalDisk;

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
        }
        return writeCommandOuts;
    }
}
