package MultiReader;

import java.util.ArrayList;

import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;

public class DefaultReader implements MultiReaderStrategy {
    @Override
    public ReadCommandOut read(ArrayList<ReadCommandIn> readCommandIns) {
        return null;
    }
}
