package Reader;

import java.util.ArrayList;

import IO.model.ReadCommandIn;
import IO.model.ReadRetrun;

public class Reader {
    private ReaderStrategy readerStrategy;

    public Reader(String readerStrategy) {
        if (readerStrategy.equals("ReadOnly")) {
            this.readerStrategy = new ReadOnlyStrategy();
        } else if (readerStrategy.equals("default")) {
            this.readerStrategy = new DefaultReaderStrategy();
        } else {
            throw new IllegalArgumentException("Invalid reader strategy: " + readerStrategy);
        }
    }

    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        return readerStrategy.read(readCommandIns);
    }
}
