package IO.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

public class ReadRetrun {
    public Map<Integer, MultiReadCommandOut> readCommandOuts;
    public HashSet<CompleteCommandOut> completeCommandOuts;
    public ArrayList<BusyCommandOut> busyCommandOuts;
}
