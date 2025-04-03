package IO.model;

import java.util.ArrayList;
import java.util.List;

import Info.model.Action;

public class MultiReadCommandOut {
    // 外层是key为0和1，代表磁头0和磁头1，内层List代表动作序列
    public List<List<Action>> actions;
    // 只代表磁头0和磁头1的跳转目标
    public List<Integer> jumpTargets;

    public MultiReadCommandOut() {
        this.actions = new ArrayList<>(2);
        this.jumpTargets = new ArrayList<>(2);
    }

    public MultiReadCommandOut(List<List<Action>> actions, List<Integer> jumpTargets) {
        this.actions = actions;
        this.jumpTargets = jumpTargets;
    }

    /**
     * 设置磁头0或磁头1的动作序列和跳转目标
     * 
     * @param index      磁头0还是磁头1
     * @param action     动作序列
     * @param jumpTarget 跳转目标
     */
    public void setAction(int index, List<Action> action, int jumpTarget) {
        this.actions.set(index, action);
        this.jumpTargets.set(index, jumpTarget);
    }
}
