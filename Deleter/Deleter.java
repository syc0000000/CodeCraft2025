package Deleter;

import java.util.ArrayList;

import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;

public class Deleter {
    DeleteStrategy deleteStrategy;

    public Deleter(String deleteStrategy) {
        if (deleteStrategy.equals("default")) {
            this.deleteStrategy = new DefaultDeleteStrategy();
        } else if (deleteStrategy.equals("ff")) {
            this.deleteStrategy = new UnitFFDeleteStrategy();
        } else {
            throw new IllegalArgumentException("Invalid delete strategy: " + deleteStrategy);
        }
    }

    public ArrayList<DeleteCommandOut> delete(ArrayList<DeleteCommandIn> deleteCommandIns) {
        return deleteStrategy.delete(deleteCommandIns);
    }
}