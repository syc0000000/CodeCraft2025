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
        } else if (deleteStrategy.equals("tag")) {
            this.deleteStrategy = new TagDeleteStrategy();
        } else {
            throw new IllegalArgumentException("Invalid delete strategy: " + deleteStrategy);
        }
    }

    public ArrayList<DeleteCommandOut> delete(ArrayList<DeleteCommandIn> deleteCommandIns) {
        return deleteStrategy.delete(deleteCommandIns);
    }
}