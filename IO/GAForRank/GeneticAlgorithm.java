package IO.GAForRank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class GeneticAlgorithm {
    private static final ModuleLogger log = LoggerFactory.getLogger("GAForRank");

    private int populationSize; // 种群大小
    private double crossoverRate; // 交叉率
    private double mutationRate; // 变异率
    private int maxGeneration; // 最大迭代次数
    private Random random; // 随机数生成器
    private int diskId; // 硬盘ID

    // 允许提前收敛的条件
    private int maxNonImproveGenerations; // 连续多少代没有改进就提前结束
    private double improvementThreshold; // 改进必须超过这个阈值才算

    // 使用GAConfig配置的构造函数
    public GeneticAlgorithm(GAConfig config) {
        this.populationSize = config.getPopulationSize();
        this.crossoverRate = config.getCrossoverRate();
        this.mutationRate = config.getMutationRate();
        this.maxGeneration = config.getMaxGeneration();
        this.diskId = config.getDiskId();
        this.maxNonImproveGenerations = config.getMaxNonImproveGenerations();
        this.improvementThreshold = config.getImprovementThreshold();
        this.random = new Random();
    }

    // 直接指定参数的构造函数
    public GeneticAlgorithm(int populationSize, double crossoverRate, double mutationRate, int maxGeneration,
            int diskId) {
        this.populationSize = populationSize;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.maxGeneration = maxGeneration;
        this.diskId = diskId;
        this.maxNonImproveGenerations = 20; // 默认值
        this.improvementThreshold = 0.001; // 默认值
        this.random = new Random();
    }

    // 初始化种群
    private ArrayList<Gene> initPopulation(HashSet<Integer> tagSet) {
        ArrayList<Gene> population = new ArrayList<>();

        // 可以转换为数组进行操作
        ArrayList<Integer> tagArray = new ArrayList<>(tagSet);
        int tagCount = tagArray.size();

        // 一部分使用完全随机的排序
        int randomCount = populationSize * 9 / 10; // 90%的随机个体
        for (int i = 0; i < randomCount; i++) {
            // 随机生成一个排序
            ArrayList<Integer> tagRank = new ArrayList<>(tagArray);
            Collections.shuffle(tagRank);
            // 创建一个基因
            Gene gene = new Gene(tagRank, diskId);
            gene.calculateFitness();
            population.add(gene);
        }

        // 其余部分使用启发式生成序列
        int heuristicCount = populationSize - randomCount;
        for (int i = 0; i < heuristicCount; i++) {
            ArrayList<Integer> tagRank = new ArrayList<>(tagArray);

            // 对每个个体应用不同的扰动
            if (i % 3 == 0) {
                // 第一种策略：按标签ID顺序
                Collections.sort(tagRank);
            } else if (i % 3 == 1) {
                // 第二种策略：按标签ID逆序
                Collections.sort(tagRank, Collections.reverseOrder());
            } else {
                // 第三种策略：部分排序，部分随机
                Collections.sort(tagRank.subList(0, tagRank.size() / 2));
                Collections.shuffle(tagRank.subList(tagRank.size() / 2, tagRank.size()));
            }

            // 添加一些随机扰动
            for (int j = 0; j < tagCount / 4; j++) {
                int pos1 = random.nextInt(tagCount);
                int pos2 = random.nextInt(tagCount);

                // 交换两个位置
                Integer temp = tagRank.get(pos1);
                tagRank.set(pos1, tagRank.get(pos2));
                tagRank.set(pos2, temp);
            }

            // 创建一个基因
            Gene gene = new Gene(tagRank, diskId);
            gene.calculateFitness();
            population.add(gene);
        }

        // 排序，找出初始最优解
        Collections.sort(population, Comparator.comparingDouble(Gene::getFitness));
        log.info(String.format("磁盘%d: 初始种群中有%d个随机个体，%d个启发式个体",
                diskId, randomCount, heuristicCount));

        return population;
    }

    // 选择操作，使用轮盘赌选择
    private ArrayList<Gene> selection(ArrayList<Gene> population) {
        ArrayList<Gene> selected = new ArrayList<>();
        // 计算总适应度的倒数（因为适应度越小越好）
        double totalFitnessInverse = 0;
        for (Gene gene : population) {
            totalFitnessInverse += 1.0 / gene.getFitness();
        }

        // 轮盘赌选择
        for (int i = 0; i < populationSize; i++) {
            double r = random.nextDouble() * totalFitnessInverse;
            double sum = 0;
            for (Gene gene : population) {
                sum += 1.0 / gene.getFitness();
                if (sum >= r) {
                    selected.add(new Gene(gene)); // 添加深拷贝
                    break;
                }
            }
        }
        return selected;
    }

    // 交叉操作，使用更安全的顺序交叉法(OX)
    private void crossover(ArrayList<Gene> population) {
        for (int i = 0; i < populationSize; i += 2) {
            if (i + 1 < populationSize && random.nextDouble() < crossoverRate) {
                Gene parent1 = population.get(i);
                Gene parent2 = population.get(i + 1);

                ArrayList<Integer> tagRank1 = parent1.getTagRank();
                ArrayList<Integer> tagRank2 = parent2.getTagRank();
                int length = tagRank1.size();

                if (length < 2) {
                    continue; // 标签数量太少，不进行交叉
                }

                // 随机选择两个交叉点
                int point1 = random.nextInt(length);
                int point2 = random.nextInt(length);
                if (point1 > point2) {
                    int temp = point1;
                    point1 = point2;
                    point2 = temp;
                }

                // 创建子代
                ArrayList<Integer> offspring1 = new ArrayList<>(Collections.nCopies(length, -1));
                ArrayList<Integer> offspring2 = new ArrayList<>(Collections.nCopies(length, -1));

                // 1. 拷贝交叉区域
                for (int j = point1; j <= point2; j++) {
                    offspring1.set(j, tagRank1.get(j));
                    offspring2.set(j, tagRank2.get(j));
                }

                // 2. 填充剩余位置
                fillRemainingPositions(tagRank2, offspring1, point1, point2);
                fillRemainingPositions(tagRank1, offspring2, point1, point2);

                // 创建新的基因
                Gene child1 = new Gene(offspring1, diskId);
                Gene child2 = new Gene(offspring2, diskId);
                child1.calculateFitness();
                child2.calculateFitness();

                // 更新种群
                population.set(i, child1);
                population.set(i + 1, child2);
            }
        }
    }

    // 填充剩余位置的辅助方法
    private void fillRemainingPositions(ArrayList<Integer> source, ArrayList<Integer> target, int point1, int point2) {
        int length = source.size();

        // 创建已使用标签的集合
        HashSet<Integer> usedTags = new HashSet<>();
        for (int j = point1; j <= point2; j++) {
            if (target.get(j) != -1) {
                usedTags.add(target.get(j));
            }
        }

        // 填充剩余位置
        int targetIndex = (point2 + 1) % length; // 从交叉区域后开始填充
        for (int j = 0; j < length; j++) {
            int sourceIndex = (point2 + 1 + j) % length; // 从源数组的交叉区域后开始查找
            int value = source.get(sourceIndex);

            if (!usedTags.contains(value)) {
                target.set(targetIndex, value);
                targetIndex = (targetIndex + 1) % length;

                // 如果到达交叉区域起点，跳过交叉区域
                if (targetIndex == point1) {
                    targetIndex = (point2 + 1) % length;
                }
            }
        }
    }

    // 变异操作，使用多种变异策略
    private void mutation(ArrayList<Gene> population) {
        for (Gene gene : population) {
            if (random.nextDouble() < mutationRate) {
                // 随机选择变异策略
                int strategy = random.nextInt(3);
                ArrayList<Integer> tagRank = gene.getTagRank();
                int length = tagRank.size();

                if (length < 2)
                    continue;

                switch (strategy) {
                    case 0: // 单点交换变异
                        // 随机选择两个位置
                        int pos1 = random.nextInt(length);
                        int pos2 = random.nextInt(length);

                        // 使用优化的交换方法
                        gene.swapTags(pos1, pos2);
                        break;

                    case 1: // 部分逆序变异
                        // 随机选择一个连续子序列并逆序
                        int start = random.nextInt(length);
                        int end = start + random.nextInt(length - start);
                        if (end >= length)
                            end = length - 1;

                        // 逆序子序列
                        for (int i = start, j = end; i < j; i++, j--) {
                            gene.swapTags(i, j);
                        }
                        break;

                    case 2: // 多点交换变异
                        // 随机交换多个位置的标签
                        int swapCount = 1 + random.nextInt(Math.min(5, length / 2));
                        for (int i = 0; i < swapCount; i++) {
                            int p1 = random.nextInt(length);
                            int p2 = random.nextInt(length);
                            gene.swapTags(p1, p2);
                        }
                        break;
                }

                // 重新计算适应度
                gene.calculateFitness();
            }
        }
    }

    // 获取最优个体
    private Gene getBestGene(ArrayList<Gene> population) {
        Gene bestGene = population.get(0);
        for (Gene gene : population) {
            if (gene.getFitness() < bestGene.getFitness()) {
                bestGene = gene;
            }
        }
        return bestGene;
    }

    // 计算种群适应度统计信息
    private double[] calculateFitnessStats(ArrayList<Gene> population) {
        double total = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (Gene gene : population) {
            double fitness = gene.getFitness();
            total += fitness;
            if (fitness < min)
                min = fitness;
            if (fitness > max)
                max = fitness;
        }

        return new double[] {
                min, // 最小值
                max, // 最大值
                total / population.size() // 平均值
        };
    }

    // 排序得到Tag的最优排序
    public ArrayList<Integer> evolve(HashSet<Integer> tagSet) {
        if (tagSet == null || tagSet.isEmpty()) {
            log.info("磁盘" + diskId + ": 标签集合为空，跳过优化");
            return new ArrayList<>();
        }

        if (tagSet.size() == 1) {
            log.info("磁盘" + diskId + ": 标签集合仅有一个元素，无需优化");
            return new ArrayList<>(tagSet);
        }

        log.info(String.format("磁盘%d: 开始遗传算法，标签数量=%d，种群大小=%d，最大代数=%d，连续无改进代数=%d，改进阈值=%.6f",
                diskId, tagSet.size(), populationSize, maxGeneration, maxNonImproveGenerations, improvementThreshold));

        try {
            // 记录起始时间
            long startTime = System.currentTimeMillis();

            // 初始化种群
            ArrayList<Gene> population = initPopulation(tagSet);

            // 记录最优个体
            Gene bestGene = getBestGene(population);
            double bestFitness = bestGene.getFitness();
            log.info(String.format("磁盘%d: 初始种群最优适应度=%.2f", diskId, bestFitness));

            // 用于提前收敛判断
            int nonImproveCount = 0;
            int lastReportedGeneration = 0;

            // 迭代进化
            for (int generation = 0; generation < maxGeneration; generation++) {
                // 记录迭代开始时间
                long iterationStartTime = System.currentTimeMillis();

                try {
                    // 选择
                    population = selection(population);

                    // 交叉
                    crossover(population);

                    // 变异
                    mutation(population);

                    // 更新最优个体
                    Gene currentBest = getBestGene(population);
                    double currentBestFitness = currentBest.getFitness();

                    // 计算迭代耗时
                    long iterationEndTime = System.currentTimeMillis();
                    long iterationTime = iterationEndTime - iterationStartTime;

                    // 判断是否有显著改进
                    boolean hasImprovement = false;
                    if (currentBestFitness < bestFitness) {
                        double improvementRate = (bestFitness - currentBestFitness) / bestFitness;

                        // 记录新的最优解
                        bestGene = new Gene(currentBest);
                        bestFitness = currentBestFitness;

                        // 判断改进是否显著
                        if (improvementRate > improvementThreshold) {
                            hasImprovement = true;
                            nonImproveCount = 0;

                            log.info(String.format("磁盘%d: 第%d代发现显著改进，适应度=%.2f，改进率=%.6f%%",
                                    diskId, generation + 1, bestFitness, improvementRate * 100));
                        } else {
                            nonImproveCount++;

                            if (generation % 10 == 0) {
                                log.info(String.format("磁盘%d: 第%d代发现微小改进，适应度=%.2f，改进率=%.6f%%",
                                        diskId, generation + 1, bestFitness, improvementRate * 100));
                            }
                        }
                    } else {
                        nonImproveCount++;
                    }

                    // 每50代输出一次进度报告
                    if (generation - lastReportedGeneration >= 50) {
                        log.info(String.format("磁盘%d: 已迭代%d代，当前最优适应度=%.2f，无改进代数=%d",
                                diskId, generation + 1, bestFitness, nonImproveCount));
                        lastReportedGeneration = generation;
                    }

                    // 提前收敛条件
                    if (nonImproveCount >= maxNonImproveGenerations) {
                        log.info(String.format("磁盘%d: 连续%d代无显著改进，提前结束迭代", diskId, nonImproveCount));
                        break;
                    }
                } catch (Exception e) {
                    log.error("磁盘" + diskId + ": 第" + generation + "代迭代出错: " + e.getMessage());
                    // 捕获异常但继续下一代迭代
                }
            }

            // 计算总耗时
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            // 获取最终排序结果
            ArrayList<Integer> bestRank = bestGene.getTagRank();

            // 输出最终结果
            log.info(String.format("磁盘%d: 遗传算法完成，总耗时=%d毫秒，最终适应度=%.2f",
                    diskId, totalTime, bestFitness));

            // 如果标签数量较少，输出完整排序；否则只输出前10个和后10个
            if (bestRank.size() <= 20) {
                log.info(String.format("磁盘%d: 最优排序结果: %s", diskId, bestRank.toString()));
            } else {
                ArrayList<Integer> prefix = new ArrayList<>(bestRank.subList(0, 10));
                ArrayList<Integer> suffix = new ArrayList<>(bestRank.subList(bestRank.size() - 10, bestRank.size()));
                log.info(String.format("磁盘%d: 最优排序前10个标签: %s", diskId, prefix.toString()));
                log.info(String.format("磁盘%d: 最优排序后10个标签: %s", diskId, suffix.toString()));
            }

            // 返回最优排序
            return bestRank;
        } catch (Exception e) {
            log.error("磁盘" + diskId + ": 遗传算法执行失败: " + e.getMessage());
            e.printStackTrace();
            // 返回一个随机排序作为备选
            ArrayList<Integer> fallbackRank = new ArrayList<>(tagSet);
            Collections.shuffle(fallbackRank);
            return fallbackRank;
        }
    }
}