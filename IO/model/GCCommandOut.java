package IO.model;

import java.util.ArrayList;

/**
 * 一个磁盘的垃圾回收命令
 */
public class GCCommandOut {
    public int size; // 垃圾回收操作次数
    public ArrayList<Integer> s;
    public ArrayList<Integer> t;

    public GCCommandOut(ArrayList<Integer> s, ArrayList<Integer> t) {
        size = s.size();
        this.s = s;
        this.t = t;
    }

    public GCCommandOut() {
        size = 0;
        s = new ArrayList<>();
        t = new ArrayList<>();
    }


    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(size).append("\n");
        for (int i = 0; i < size; i++) {
            result.append(s.get(i)).append(" ").append(t.get(i)).append("\n");
        }
        return result.toString();
    }
}
