package IO.model;

import java.util.ArrayList;

/**
 * 一个磁盘的垃圾回收命令
 */
public class GCCommandOut {
    int n_gc; // 垃圾回收操作次数
    ArrayList<Integer> s;
    ArrayList<Integer> t;

    public GCCommandOut(ArrayList<Integer> s, ArrayList<Integer> t) {
        n_gc = s.size();
        this.s = s;
        this.t = t;
    }

    public GCCommandOut() {
        n_gc = 0;
        s = new ArrayList<>();
        t = new ArrayList<>();
    }


    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(n_gc).append("\n");
        for (int i = 0; i < n_gc; i++) {
            result.append(s.get(i)).append(" ").append(t.get(i)).append("\n");
        }
        return result.toString();
    }
}
