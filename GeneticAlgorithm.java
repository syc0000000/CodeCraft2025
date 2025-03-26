import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 遗传算法实现类
 */
public class GeneticAlgorithm {
    private final Random random = new Random();

    // 使用固定变异率
    private static final double MUTATION_RATE = GeneticDiskDistribution.MUTATION_RATE_START;

    /**
     * 运行遗传算法
     * 
     * @return 最佳个体
     */
    public Individual run() {
        // 初始化种群
        List<Individual> population = new ArrayList<>(GeneticDiskDistribution.POPULATION_SIZE);
        for (int i = 0; i < GeneticDiskDistribution.POPULATION_SIZE; i++) {
            population.add(createIndividual());
        }

        // 收敛计数器
        int stagnationCounter = 0;
        double bestFitnessEver = Double.POSITIVE_INFINITY;
        Individual bestIndividualEver = null;

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(GeneticDiskDistribution.THREADS);

        try {
            // 主循环
            for (int generation = 0; generation < GeneticDiskDistribution.NUM_GENERATIONS; generation++) {
                // 使用固定变异率替代动态变异率
                // double currentMutationRate = calculateMutationRate(generation,
                // GeneticDiskDistribution.NUM_GENERATIONS);

                // 并行评估适应度
                final List<Individual> currentPopulation = population;
                List<Future<Void>> futures = new ArrayList<>();

                int chunkSize = Math.max(1, GeneticDiskDistribution.POPULATION_SIZE / GeneticDiskDistribution.THREADS);
                for (int i = 0; i < GeneticDiskDistribution.POPULATION_SIZE; i += chunkSize) {
                    final int start = i;
                    final int end = Math.min(i + chunkSize, GeneticDiskDistribution.POPULATION_SIZE);

                    futures.add(executor.submit(() -> {
                        for (int j = start; j < end; j++) {
                            Individual individual = currentPopulation.get(j);
                            double fitness = Utils.evaluateFitness(individual);
                            individual.setFitness(fitness);
                        }
                        return null;
                    }));
                }

                // 等待所有评估完成
                for (Future<Void> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // 找出这一代的最佳个体
                Individual bestIndividual = population.stream()
                        .min(Comparator.comparingDouble(Individual::getFitness))
                        .orElse(population.get(0));
                double bestFitness = bestIndividual.getFitness();
                double avgFitness = population.stream()
                        .mapToDouble(Individual::getFitness)
                        .average()
                        .orElse(0);

                // 检查是否有新的最佳解
                if (bestFitness < bestFitnessEver) {
                    bestFitnessEver = bestFitness;
                    bestIndividualEver = bestIndividual.copy();
                    stagnationCounter = 0;
                } else {
                    stagnationCounter++;
                }

                // 每10代输出一次进度
                if (generation % 10 == 0) {
                    System.out.printf(
                            "Generation %d: Best Fitness = %.4f, Average Fitness = %.4f, Mutation Rate = %.4f%n",
                            generation, bestFitness, avgFitness, MUTATION_RATE);
                }

                // 检查是否应该提前停止
                if (stagnationCounter >= GeneticDiskDistribution.CONVERGENCE_THRESHOLD) {
                    System.out.printf("Early stopping at generation %d due to no improvement for %d generations%n",
                            generation, GeneticDiskDistribution.CONVERGENCE_THRESHOLD);
                    break;
                }

                // 保留精英
                population.sort(Comparator.comparingDouble(Individual::getFitness));
                List<Individual> newPopulation = new ArrayList<>(GeneticDiskDistribution.POPULATION_SIZE);
                for (int i = 0; i < GeneticDiskDistribution.ELITE_SIZE; i++) {
                    newPopulation.add(population.get(i).copy());
                }

                // 生成新一代
                List<Future<Individual>> offspringFutures = new ArrayList<>();
                final List<Individual> finalPopulation = population; // 创建一个final引用
                for (int i = 0; i < GeneticDiskDistribution.POPULATION_SIZE - GeneticDiskDistribution.ELITE_SIZE; i++) {
                    offspringFutures.add(executor.submit(() -> createOffspring(finalPopulation, MUTATION_RATE)));
                }

                // 收集后代
                for (Future<Individual> future : offspringFutures) {
                    try {
                        newPopulation.add(future.get());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // 更新种群
                population = newPopulation;
            }
        } finally {
            executor.shutdown();
        }

        return bestIndividualEver;
    }

    /**
     * 创建一个随机的解决方案
     * 
     * @return 新个体
     */
    private Individual createIndividual() {
        Individual individual = new Individual();

        // 为每个标签分配空间
        for (int tagId = 0; tagId < GeneticDiskDistribution.TAGS.length; tagId++) {
            // 决定分割数量 (1到MAX_SPLITS_PER_TAG)
            int numSplits = random.nextInt(Math.min(GeneticDiskDistribution.MAX_SPLITS_PER_TAG,
                    GeneticDiskDistribution.NUM_DISKS)) + 1;

            // 随机选择磁盘
            List<Integer> diskIndices = getRandomSample(GeneticDiskDistribution.NUM_DISKS, numSplits);

            // 分配随机比例
            double[] proportions = new double[numSplits];
            double proportionsSum = 0;
            for (int i = 0; i < numSplits; i++) {
                proportions[i] = random.nextDouble();
                proportionsSum += proportions[i];
            }

            // 归一化比例
            for (int i = 0; i < numSplits; i++) {
                proportions[i] /= proportionsSum;
            }

            // 添加到选定的磁盘上
            for (int i = 0; i < numSplits; i++) {
                individual.addTagToDisk(diskIndices.get(i), tagId, proportions[i]);
            }
        }

        // 如果有磁盘的标签数量超过上限，修复解决方案
        repairIndividual(individual);

        return individual;
    }

    /**
     * 修复个体，确保满足约束条件
     * 
     * @param individual 待修复的个体
     */
    private void repairIndividual(Individual individual) {
        // 如果有磁盘的标签数量超过上限，修复解决方案
        for (int diskIdx = 0; diskIdx < GeneticDiskDistribution.NUM_DISKS; diskIdx++) {
            List<TagAllocation> disk = individual.getDisk(diskIdx);
            while (disk.size() > GeneticDiskDistribution.MAX_TAGS_PER_DISK) {
                // 随机选择一个标签移除
                int tagToRemoveIdx = random.nextInt(disk.size());
                TagAllocation tagToRemove = disk.remove(tagToRemoveIdx);
                int tagId = tagToRemove.getTagId();
                double proportion = tagToRemove.getProportion();

                // 找一个标签数量少的磁盘
                List<Integer> validDisks = new ArrayList<>();
                for (int i = 0; i < GeneticDiskDistribution.NUM_DISKS; i++) {
                    if (i != diskIdx && individual.getDisk(i).size() < GeneticDiskDistribution.MAX_TAGS_PER_DISK &&
                            !hasTag(individual.getDisk(i), tagId)) {
                        validDisks.add(i);
                    }
                }

                if (!validDisks.isEmpty()) {
                    int targetDisk = validDisks.get(random.nextInt(validDisks.size()));
                    individual.addTagToDisk(targetDisk, tagId, proportion);
                } else {
                    // 如果没有可用磁盘，重新分配这个比例到已有该标签的磁盘
                    List<Integer> disksWithTag = new ArrayList<>();
                    for (int i = 0; i < GeneticDiskDistribution.NUM_DISKS; i++) {
                        if (hasTag(individual.getDisk(i), tagId)) {
                            disksWithTag.add(i);
                        }
                    }

                    if (!disksWithTag.isEmpty()) {
                        int targetDisk = disksWithTag.get(random.nextInt(disksWithTag.size()));
                        // 找到这个标签在目标磁盘中的位置
                        for (TagAllocation tag : individual.getDisk(targetDisk)) {
                            if (tag.getTagId() == tagId) {
                                tag.setProportion(tag.getProportion() + proportion);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // 确保所有磁盘至少有一个标签
        for (int diskIdx = 0; diskIdx < GeneticDiskDistribution.NUM_DISKS; diskIdx++) {
            if (individual.getDisk(diskIdx).isEmpty()) {
                // 从标签较多的磁盘借一个
                int donorDisk = 0;
                int maxTags = 0;
                for (int i = 0; i < GeneticDiskDistribution.NUM_DISKS; i++) {
                    if (individual.getDisk(i).size() > maxTags) {
                        maxTags = individual.getDisk(i).size();
                        donorDisk = i;
                    }
                }

                if (individual.getDisk(donorDisk).size() > 1) { // 确保捐赠后还有标签
                    int tagToMoveIdx = random.nextInt(individual.getDisk(donorDisk).size());
                    TagAllocation tagToMove = individual.getDisk(donorDisk).remove(tagToMoveIdx);
                    individual.addTagToDisk(diskIdx, tagToMove.getTagId(), tagToMove.getProportion());
                }
            }
        }

        // 归一化每个标签的比例总和为1
        individual.normalizeTagProportions();
    }

    /**
     * 检查磁盘上是否有特定标签
     * 
     * @param disk  磁盘标签列表
     * @param tagId 要检查的标签ID
     * @return 是否存在
     */
    private boolean hasTag(List<TagAllocation> disk, int tagId) {
        for (TagAllocation tag : disk) {
            if (tag.getTagId() == tagId) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取随机样本（不重复）
     * 
     * @param range 范围（0到range-1）
     * @param count 样本数量
     * @return 随机样本列表
     */
    private List<Integer> getRandomSample(int range, int count) {
        // 如果count接近range，则打乱整个列表
        if (count > range / 2) {
            List<Integer> result = IntStream.range(0, range).boxed().collect(Collectors.toList());
            Collections.shuffle(result, random);
            return result.subList(0, count);
        } else {
            // 否则使用Set确保不重复
            Set<Integer> selected = new HashSet<>();
            while (selected.size() < count) {
                selected.add(random.nextInt(range));
            }
            return new ArrayList<>(selected);
        }
    }

    /**
     * 创建一个后代
     * 
     * @param population   当前种群
     * @param mutationRate 当前变异率
     * @return 新的个体
     */
    private Individual createOffspring(List<Individual> population, double mutationRate) {
        // 锦标赛选择两个父代
        Individual parent1 = tournamentSelect(population);
        Individual parent2 = tournamentSelect(population);

        // 应用交叉
        Individual child;
        if (random.nextDouble() < GeneticDiskDistribution.CROSSOVER_RATE) {
            child = crossover(parent1, parent2);
        } else {
            child = random.nextBoolean() ? parent1.copy() : parent2.copy();
        }

        // 应用变异
        if (random.nextDouble() < mutationRate) {
            mutate(child, mutationRate);
        }

        return child;
    }

    /**
     * 锦标赛选择
     * 
     * @param population 种群
     * @return 选中的个体
     */
    private Individual tournamentSelect(List<Individual> population) {
        Individual best = null;
        double bestFitness = Double.POSITIVE_INFINITY;

        for (int i = 0; i < GeneticDiskDistribution.TOURNAMENT_SIZE; i++) {
            Individual candidate = population.get(random.nextInt(population.size()));
            if (best == null || candidate.getFitness() < bestFitness) {
                best = candidate;
                bestFitness = candidate.getFitness();
            }
        }

        return best;
    }

    /**
     * 交叉两个父代，生成一个新的子代
     * 
     * @param parent1 父代1
     * @param parent2 父代2
     * @return 子代
     */
    private Individual crossover(Individual parent1, Individual parent2) {
        Individual child = new Individual();

        // 改进的交叉策略：半均匀交叉
        for (int diskIdx = 0; diskIdx < GeneticDiskDistribution.NUM_DISKS; diskIdx++) {
            if (random.nextBoolean()) {
                for (TagAllocation tag : parent1.getDisk(diskIdx)) {
                    child.addTagToDisk(diskIdx, tag.getTagId(), tag.getProportion());
                }
            } else {
                for (TagAllocation tag : parent2.getDisk(diskIdx)) {
                    child.addTagToDisk(diskIdx, tag.getTagId(), tag.getProportion());
                }
            }
        }

        // 修复子代
        repairIndividual(child);

        return child;
    }

    /**
     * 变异个体
     * 
     * @param individual   要变异的个体
     * @param mutationRate 变异率
     */
    private void mutate(Individual individual, double mutationRate) {
        // 选择一个随机磁盘
        int diskIdx = random.nextInt(GeneticDiskDistribution.NUM_DISKS);

        // 随机选择突变类型
        double mutationType = random.nextDouble();

        if (mutationType < 0.33 && !individual.getDisk(diskIdx).isEmpty()) { // 移除一个标签
            if (individual.getDisk(diskIdx).size() > GeneticDiskDistribution.MIN_TAGS_PER_DISK) {
                // 选择负载贡献最大的标签来移除
                int maxTagIdx = 0;
                double maxContribution = 0;
                List<TagAllocation> disk = individual.getDisk(diskIdx);

                for (int i = 0; i < disk.size(); i++) {
                    TagAllocation tag = disk.get(i);
                    double contribution = GeneticDiskDistribution.TAGS[tag.getTagId()] * tag.getProportion();
                    if (contribution > maxContribution) {
                        maxContribution = contribution;
                        maxTagIdx = i;
                    }
                }

                TagAllocation tagToMove = disk.remove(maxTagIdx);
                int tagId = tagToMove.getTagId();
                double proportion = tagToMove.getProportion();

                // 找一个负载最小的磁盘
                double[] diskLoads = Utils.calculateDiskLoads(individual);
                int targetDisk = 0;
                double minLoad = Double.POSITIVE_INFINITY;

                for (int i = 0; i < GeneticDiskDistribution.NUM_DISKS; i++) {
                    if (i != diskIdx && diskLoads[i] < minLoad) {
                        minLoad = diskLoads[i];
                        targetDisk = i;
                    }
                }

                // 检查目标磁盘是否已有该标签
                boolean hasTag = false;
                for (TagAllocation tag : individual.getDisk(targetDisk)) {
                    if (tag.getTagId() == tagId) {
                        tag.setProportion(tag.getProportion() + proportion);
                        hasTag = true;
                        break;
                    }
                }

                if (!hasTag && individual.getDisk(targetDisk).size() < GeneticDiskDistribution.MAX_TAGS_PER_DISK) {
                    individual.addTagToDisk(targetDisk, tagId, proportion);
                } else if (!hasTag) {
                    // 如果目标磁盘已满，分配给其他有此标签的磁盘
                    List<Integer> disksWithTag = new ArrayList<>();
                    for (int i = 0; i < GeneticDiskDistribution.NUM_DISKS; i++) {
                        if (i != diskIdx) {
                            for (TagAllocation tag : individual.getDisk(i)) {
                                if (tag.getTagId() == tagId) {
                                    disksWithTag.add(i);
                                    break;
                                }
                            }
                        }
                    }

                    if (!disksWithTag.isEmpty()) {
                        int redistributionDisk = disksWithTag.get(random.nextInt(disksWithTag.size()));
                        for (TagAllocation tag : individual.getDisk(redistributionDisk)) {
                            if (tag.getTagId() == tagId) {
                                tag.setProportion(tag.getProportion() + proportion);
                                break;
                            }
                        }
                    }
                }

                // 归一化
                individual.normalizeTagProportions();
            }
        } else if (mutationType < 0.66 && !individual.getDisk(diskIdx).isEmpty()) { // 修改标签比例
            // 选择较大的标签来调整比例
            int maxTagIdx = 0;
            double maxSize = 0;
            List<TagAllocation> disk = individual.getDisk(diskIdx);

            for (int i = 0; i < disk.size(); i++) {
                TagAllocation tag = disk.get(i);
                double size = GeneticDiskDistribution.TAGS[tag.getTagId()] * tag.getProportion();
                if (size > maxSize) {
                    maxSize = size;
                    maxTagIdx = i;
                }
            }

            TagAllocation tag = disk.get(maxTagIdx);
            int tagId = tag.getTagId();
            double oldProportion = tag.getProportion();

            // 随机调整比例
            double change = (random.nextDouble() - 0.5) * 0.4 * mutationRate;
            double newProportion = Math.max(0.01, Math.min(0.99, oldProportion + change));
            double delta = newProportion - oldProportion;

            // 找出其他包含此标签的磁盘
            List<Integer> otherDisksWithTag = new ArrayList<>();
            List<Double> otherProportions = new ArrayList<>();

            for (int i = 0; i < GeneticDiskDistribution.NUM_DISKS; i++) {
                if (i != diskIdx) {
                    for (TagAllocation t : individual.getDisk(i)) {
                        if (t.getTagId() == tagId) {
                            otherDisksWithTag.add(i);
                            otherProportions.add(t.getProportion());
                            break;
                        }
                    }
                }
            }

            if (!otherDisksWithTag.isEmpty()) {
                // 从其他磁盘减去增加的比例
                double totalOtherProportion = otherProportions.stream().mapToDouble(Double::doubleValue).sum();

                for (int i = 0; i < otherDisksWithTag.size(); i++) {
                    int diskId = otherDisksWithTag.get(i);
                    double prop = otherProportions.get(i);
                    double adjustment = (delta * prop) / totalOtherProportion;

                    for (TagAllocation t : individual.getDisk(diskId)) {
                        if (t.getTagId() == tagId) {
                            t.setProportion(Math.max(0.01, prop - adjustment));
                            break;
                        }
                    }
                }

                // 更新当前磁盘的比例
                tag.setProportion(newProportion);

                // 重新归一化
                individual.normalizeTagProportions();
            }
        } else { // 交换两个磁盘之间的标签
            if (!individual.getDisk(diskIdx).isEmpty()) {
                // 计算每个磁盘的负载
                double[] diskLoads = Utils.calculateDiskLoads(individual);

                // 找出负载最大和最小的磁盘
                int maxLoadDisk = 0;
                int minLoadDisk = 0;
                double maxLoad = diskLoads[0];
                double minLoad = diskLoads[0];

                for (int i = 1; i < GeneticDiskDistribution.NUM_DISKS; i++) {
                    if (diskLoads[i] > maxLoad) {
                        maxLoad = diskLoads[i];
                        maxLoadDisk = i;
                    }
                    if (diskLoads[i] < minLoad) {
                        minLoad = diskLoads[i];
                        minLoadDisk = i;
                    }
                }

                if (!individual.getDisk(maxLoadDisk).isEmpty() && !individual.getDisk(minLoadDisk).isEmpty()) {
                    // 从高负载磁盘选择一个大标签
                    int maxTagIdx = 0;
                    double maxContribution = 0;
                    List<TagAllocation> maxDisk = individual.getDisk(maxLoadDisk);

                    for (int i = 0; i < maxDisk.size(); i++) {
                        TagAllocation tag = maxDisk.get(i);
                        double contribution = GeneticDiskDistribution.TAGS[tag.getTagId()] * tag.getProportion();
                        if (contribution > maxContribution) {
                            maxContribution = contribution;
                            maxTagIdx = i;
                        }
                    }

                    // 从低负载磁盘选择一个小标签
                    int minTagIdx = 0;
                    double minContribution = Double.POSITIVE_INFINITY;
                    List<TagAllocation> minDisk = individual.getDisk(minLoadDisk);

                    for (int i = 0; i < minDisk.size(); i++) {
                        TagAllocation tag = minDisk.get(i);
                        double contribution = GeneticDiskDistribution.TAGS[tag.getTagId()] * tag.getProportion();
                        if (contribution < minContribution) {
                            minContribution = contribution;
                            minTagIdx = i;
                        }
                    }

                    // 交换标签
                    TagAllocation temp = maxDisk.get(maxTagIdx);
                    maxDisk.set(maxTagIdx, minDisk.get(minTagIdx));
                    minDisk.set(minTagIdx, temp);
                }
            }
        }
    }
}