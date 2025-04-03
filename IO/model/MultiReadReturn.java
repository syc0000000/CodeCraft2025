package IO.model;

import java.util.Map;
import java.util.HashSet;

public class MultiReadReturn {
    public Map<Integer, MultiReadCommandOut> multiReadCommandOuts;
    public HashSet<CompleteCommandOut> completeCommandOuts;
    public HashSet<BusyCommandOut> busyCommandOuts;
}
