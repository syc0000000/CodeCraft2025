package Reader;

import java.util.*;

import Info.Info.Action;

public class SequenceOptimizer {
    // r操作的成本列表
    private static final int[] R_COSTS = { 64, 52, 42, 34, 28, 23, 19, 16 };

    /**
     * 优化序列
     * 
     * @param sequence 原始操作序列
     * @return 包含优化后序列和最小成本的结果
     */
    public static Result optimizeSequence(ArrayList<Action> sequence) {
        int n = sequence.size();

        // 创建动态规划表 dp[i][j] 表示到序列的第i个位置，有j个连续r操作的最小成本
        // j从0开始，0表示最后一个操作是p，j>0表示有j个连续的r
        double[][] dp = new double[n][n + 1];
        int[][] choice = new int[n][n + 1];

        // 初始化dp表
        for (int i = 0; i < n; i++) {
            Arrays.fill(dp[i], Double.POSITIVE_INFINITY);
            Arrays.fill(choice[i], -1);
        }

        // 初始化第一个操作
        if (sequence.get(0) == Action.READ) {
            dp[0][1] = R_COSTS[0]; // 第一个r的成本是64
        } else { // 'p'
            dp[0][0] = 1; // 保持为p
            dp[0][1] = R_COSTS[0]; // 变为r
            choice[0][0] = 0; // 保持为p
            choice[0][1] = 1; // 变为r
        }

        // 填充动态规划表
        for (int i = 1; i < n; i++) {
            if (sequence.get(i) == Action.READ) {
                // 必须是r，考虑前一个操作
                for (int j = 1; j < i + 2; j++) {
                    // r之后跟r，延续连续r
                    if (j > 1 && dp[i - 1][j - 1] != Double.POSITIVE_INFINITY) {
                        int rCost = R_COSTS[Math.min(j - 1, R_COSTS.length - 1)];
                        if (dp[i - 1][j - 1] + rCost < dp[i][j]) {
                            dp[i][j] = dp[i - 1][j - 1] + rCost;
                            choice[i][j] = j - 1;
                        }
                    }
                    // p之后跟r，重新开始连续r
                    if (dp[i - 1][0] != Double.POSITIVE_INFINITY) {
                        if (dp[i - 1][0] + R_COSTS[0] < dp[i][1]) {
                            dp[i][1] = dp[i - 1][0] + R_COSTS[0];
                            choice[i][1] = 0;
                        }
                    }
                }
            } else { // 'p'
                // 可以保持为p
                for (int j = 0; j < i + 1; j++) {
                    if (dp[i - 1][j] != Double.POSITIVE_INFINITY && dp[i - 1][j] + 1 < dp[i][0]) {
                        dp[i][0] = dp[i - 1][j] + 1;
                        choice[i][0] = j;
                    }
                }

                // 可以变为r
                for (int j = 1; j < i + 2; j++) {
                    // r之后跟r，延续连续r
                    if (j > 1 && dp[i - 1][j - 1] != Double.POSITIVE_INFINITY) {
                        int rCost = R_COSTS[Math.min(j - 1, R_COSTS.length - 1)];
                        if (dp[i - 1][j - 1] + rCost < dp[i][j]) {
                            dp[i][j] = dp[i - 1][j - 1] + rCost;
                            choice[i][j] = j - 1;
                        }
                    }
                    // p之后跟r，重新开始连续r
                    if (dp[i - 1][0] != Double.POSITIVE_INFINITY) {
                        if (dp[i - 1][0] + R_COSTS[0] < dp[i][1]) {
                            dp[i][1] = dp[i - 1][0] + R_COSTS[0];
                            choice[i][1] = 0;
                        }
                    }
                }
            }
        }

        // 找出最小成本和对应的结束状态
        double minCost = Double.POSITIVE_INFINITY;
        int endState = -1;
        for (int j = 0; j <= n; j++) {
            if (dp[n - 1][j] < minCost) {
                minCost = dp[n - 1][j];
                endState = j;
            }
        }

        // 重建最优解
        Action[] result = new Action[n];
        
        int i = n - 1;
        int j = endState;

        while (i >= 0) {
            if (j == 0) { // 当前是p
            result[i] = Action.PASS;
            j = choice[i][j];
            } else { // 当前是r
            result[i] = Action.READ;
            j = choice[i][j];
            }
            i--;
        }

        return new Result(new ArrayList<>(Arrays.asList(result)), (int) minCost);
    }

    /**
     * 计算序列的成本
     * 
     * @param sequence 操作序列
     * @return 总成本
     */
    public static int calculateCost(ArrayList<Action> sequence) {
        int cost = 0;
        int consecutiveR = 0;

        for (Action op : sequence) {
            if (op == Action.READ) {
                consecutiveR++;
                int rCostIndex = Math.min(consecutiveR - 1, R_COSTS.length - 1);
                cost += R_COSTS[rCostIndex];
            } else { // 'p'
                cost += 1;
                consecutiveR = 0;
            }
        }

        return cost;
    }

    /**
     * 表示优化结果的类
     */
    public static class Result {
        public final ArrayList<Action> sequence;
        public final int cost;

        public Result(ArrayList<Action> sequence, int cost) {
            this.sequence = sequence;
            this.cost = cost;
        }
        public Result() {
            this.sequence = new ArrayList<>();
            this.cost = 0;
        }
    }
}