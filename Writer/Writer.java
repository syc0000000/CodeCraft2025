package Writer;

import java.util.ArrayList;

import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;

public class Writer {
    WriteStrategy writeStrategy;

    public Writer(String writeStrategy) {
        if (writeStrategy.equals("default")) {
            this.writeStrategy = new DefaultWriteStrategy();
        } else if (writeStrategy.equals("rw")) {
            this.writeStrategy = new RWWriteStrategy();
        } else {
            throw new IllegalArgumentException("Invalid write strategy: " + writeStrategy);
        }
    }

    public ArrayList<WriteCommandOut> write(ArrayList<WriteCommandIn> writeCommandIns) {
        return writeStrategy.write(writeCommandIns);
    }

}
