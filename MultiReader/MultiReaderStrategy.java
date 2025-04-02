package MultiReader;

import java.util.ArrayList;

import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;

public interface MultiReaderStrategy {
    public ReadCommandOut read(ArrayList<ReadCommandIn> readCommandIns);
}
