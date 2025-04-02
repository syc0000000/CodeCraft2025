package MultiReader;

import java.util.ArrayList;

import IO.model.ReadCommandIn;
import IO.model.ReadRetrun;

public class MultiReader {
    private MultiReaderStrategy multiReaderStrategy;

    public MultiReader(String multiReaderStrategy) {
        if (multiReaderStrategy.equals("default")) {
            this.multiReaderStrategy = new DefaultReader();
        } else {
            throw new IllegalArgumentException("Invalid multi reader strategy: " + multiReaderStrategy);
        }
    }

    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        return null;

    }
}