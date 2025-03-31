package IO.model;

import java.util.ArrayList;
import java.util.List;

import Info.model.Action;

/**
 * 读命令输出-选手
 */
public class ReadCommandOut {
    public List<Action> actions; // 动作序列，注意顺序
    public int jumpTarget; // 跳转目标，当且仅当actions[0]为JUMP时有效

    public ReadCommandOut() {
        this.jumpTarget = -1;
        this.actions = new ArrayList<>();

    }

    public ReadCommandOut(List<Action> actions) {
        this.actions = actions;
        this.jumpTarget = -1;
    }

    public ReadCommandOut(List<Action> actions, int jumpTarget) {
        this.actions = actions;
        this.jumpTarget = jumpTarget;
    }
}