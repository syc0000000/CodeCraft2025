package Writer;

import java.util.ArrayList;

import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;

public interface WriteStrategy {
    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns);
}
