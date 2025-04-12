package MultiReader;

import java.util.HashSet;

import IO.model.CompleteCommandOut;
import IO.model.MultiReadCommandOut;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public interface MultiReaderStrategy {
    public static final ModuleLogger readerLogger = LoggerFactory.getLogger("Reader");

    public void read(int diskId, MultiReadCommandOut readCommandOut, HashSet<CompleteCommandOut> completeCommandOut);
}
