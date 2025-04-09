package IO.model;

import java.util.*;

// 某一个磁盘的交换情况
public class GCCommandOut {
    public static class Pair {
        public int x;
        public int y;
    }

    public ArrayList<Pair> pairs;

    public GCCommandOut() {
        pairs = new ArrayList<>();
    }
}
