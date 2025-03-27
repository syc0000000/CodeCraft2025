package IO.GA;

import java.util.ArrayList;
import IO.IO;

/**
 * 遗传算法参数配置类
 */
public class GeneticParameters {
    // 问题参数
    /**
     * 每个标签的对象数量
     * 标签对象总量由这个数组决定，用于计算磁盘上的对象负载
     */
    public static int[] TAGS;

    /**
     * 时间点标签对象数据
     * 这个矩阵用于计算不同时间段每个磁盘的负载变化，从而计算方差
     */
    public static ArrayList<ArrayList<Integer>> TAGS_PER_TIMEPOINT;

    /**
     * 磁盘总数量
     */
    public static final int NUM_DISKS = 10;

    /**
     * 每个磁盘上最多允许的标签数量
     */
    public static final int MAX_TAGS_PER_DISK = 16;

    /**
     * 每个磁盘上最少需要的标签数量
     */
    public static final int MIN_TAGS_PER_DISK = 1;

    /**
     * 每个标签最多可以分成的份数
     */
    public static final int MAX_SPLITS_PER_TAG = 10;

    /**
     * 每个标签在每个磁盘上的最小比例
     */
    public static final double MIN_TAG_PROPORTION = 0.06;

    // 遗传算法参数
    /**
     * 种群大小
     */
    public static final int POPULATION_SIZE = 40000;

    /**
     * 最大迭代代数
     */
    public static final int NUM_GENERATIONS = 100;

    /**
     * 变异率
     */
    public static final double MUTATION_RATE_START = 0.9;

    /**
     * 交叉概率
     */
    public static final double CROSSOVER_RATE = 0.85;

    /**
     * 精英数量，每代保留的最优个体数
     */
    public static final int ELITE_SIZE = 1000;

    /**
     * 锦标赛选择的大小
     */
    public static final int TOURNAMENT_SIZE = 8;

    /**
     * 收敛阈值：连续多少代没有改善则提前停止
     */
    public static final int CONVERGENCE_THRESHOLD = 50;

    /**
     * 使用的线程数量
     */
    public static final int THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    /**
     * 初始化参数
     */
    public static void init(int[] tags, ArrayList<ArrayList<Integer>> tagsPerTimepoint) {
        TAGS = tags;
        TAGS_PER_TIMEPOINT = tagsPerTimepoint;
    }
}