package Writer;

import java.util.ArrayList;

import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;

import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public interface WriteStrategy {
    // logger
    public final ModuleLogger log = LoggerFactory.getLogger("Writer");

    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns);
}
