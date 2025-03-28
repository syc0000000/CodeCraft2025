package IO.GAForRank;

/**
 * 遗传算法配置类
 */
public class GAConfig {
    // 默认配置
    public static final int DEFAULT_POPULATION_SIZE = 3000; // 默认种群大小
    public static final double DEFAULT_CROSSOVER_RATE = 0.5; // 默认交叉率
    public static final double DEFAULT_MUTATION_RATE = 0.95; // 默认变异率
    public static final int DEFAULT_MAX_GENERATION = 300; // 默认最大代数

    // 收敛配置
    public static final int DEFAULT_MAX_NON_IMPROVE = 50; // 默认连续无改进代数
    public static final double DEFAULT_IMPROVEMENT_THRESHOLD = 0.0001; // 默认改进阈值 (0.01%)

    // 当前配置
    private int populationSize; // 种群大小
    private double crossoverRate; // 交叉率
    private double mutationRate; // 变异率
    private int maxGeneration; // 最大代数
    private int diskId; // 磁盘ID
    private int maxNonImproveGenerations; // 连续无改进代数上限
    private double improvementThreshold; // 改进阈值

    // 构造函数
    public GAConfig(int diskId) {
        this.populationSize = DEFAULT_POPULATION_SIZE;
        this.crossoverRate = DEFAULT_CROSSOVER_RATE;
        this.mutationRate = DEFAULT_MUTATION_RATE;
        this.maxGeneration = DEFAULT_MAX_GENERATION;
        this.diskId = diskId;
        this.maxNonImproveGenerations = DEFAULT_MAX_NON_IMPROVE;
        this.improvementThreshold = DEFAULT_IMPROVEMENT_THRESHOLD;
    }

    // Getters
    public int getPopulationSize() {
        return populationSize;
    }

    public double getCrossoverRate() {
        return crossoverRate;
    }

    public double getMutationRate() {
        return mutationRate;
    }

    public int getMaxGeneration() {
        return maxGeneration;
    }

    public int getDiskId() {
        return diskId;
    }

    public int getMaxNonImproveGenerations() {
        return maxNonImproveGenerations;
    }

    public double getImprovementThreshold() {
        return improvementThreshold;
    }
}