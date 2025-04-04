package Writer.GA;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * 遗传算法实现类
 */
public class GeneticAlgorithm {
    private final Random random = new Random();
    private static final ModuleLogger log = LoggerFactory.getLogger("DiskGA");

    // 使用固定变异率
    private static final double MUTATION_RATE = GeneticParameters.MUTATION_RATE_START;

    /**
     * 运行遗传算法
     * 
     * @return 最佳个体
     */
    public Individual run() {
        // 输出标签数量
        log.info("遗传算法开始：处理 " + GeneticParameters.TAGS.length + " 个标签");
        for (int i = 0; i < GeneticParameters.TAGS.length; i++) {
            log.info("标签 " + i + " 大小：" + GeneticParameters.TAGS[i]);
        }

        // 初始化种群
        List<Individual> population = new ArrayList<>(GeneticParameters.POPULATION_SIZE);
        for (int i = 0; i < GeneticParameters.POPULATION_SIZE; i++) {
            population.add(createIndividual());
        }

        // 收敛计数器
        int stagnationCounter = 0;
        double bestFitnessEver = Double.POSITIVE_INFINITY;
        Individual bestIndividualEver = null;

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(GeneticParameters.THREADS);

        try {
            // 主循环
            for (int generation = 0; generation < GeneticParameters.NUM_GENERATIONS; generation++) {
                // 并行评估适应度
                final List<Individual> currentPopulation = population;
                List<Future<Void>> futures = new ArrayList<>();

                int chunkSize = Math.max(1, GeneticParameters.POPULATION_SIZE / GeneticParameters.THREADS);
                for (int i = 0; i < GeneticParameters.POPULATION_SIZE; i += chunkSize) {
                    final int start = i;
                    final int end = Math.min(i + chunkSize, GeneticParameters.POPULATION_SIZE);

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
                    log.info(String.format(
                            "Generation %d: Best Fitness = %.4f, Average Fitness = %.4f, Mutation Rate = %.4f",
                            generation, bestFitness, avgFitness, MUTATION_RATE));
                }

                // 检查是否应该提前停止
                if (stagnationCounter >= GeneticParameters.CONVERGENCE_THRESHOLD) {
                    log.info(String.format("Early stopping at generation %d due to no improvement for %d generations",
                            generation, GeneticParameters.CONVERGENCE_THRESHOLD));
                    break;
                }

                // 保留精英
                population.sort(Comparator.comparingDouble(Individual::getFitness));
                List<Individual> newPopulation = new ArrayList<>(GeneticParameters.POPULATION_SIZE);
                for (int i = 0; i < GeneticParameters.ELITE_SIZE; i++) {
                    newPopulation.add(population.get(i).copy());
                }

                // 生成新一代
                List<Future<Individual>> offspringFutures = new ArrayList<>();
                final List<Individual> finalPopulation = population; // 创建一个final引用
                for (int i = 0; i < GeneticParameters.POPULATION_SIZE - GeneticParameters.ELITE_SIZE; i++) {
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

        // 输出最终结果中包含的标签数量
        if (bestIndividualEver != null) {
            Map<Integer, List<Individual.TagDiskAllocation>> tagAllocations = bestIndividualEver.getTagAllocations();

            log.info(
                    "遗传算法完成：最终解决方案包含 " + tagAllocations.size() + " 个标签（原始有 " + GeneticParameters.TAGS.length + " 个标签）");
            log.info("最终解决方案中的标签ID：" + tagAllocations.keySet());

            // 检查标签在不同磁盘上的分布
            for (int i = 0; i < GeneticParameters.TAGS.length; i++) {
                List<Individual.DiskAllocation> tagDisks = bestIndividualEver.getTag(i);
                log.info("标签 " + i + " 分配到了 " + tagDisks.size() + " 个磁盘上，分别是：" +
                        tagDisks.stream()
                                .map(da -> String.format("磁盘%d(%.1f%%)", da.getDiskIdx(), da.getProportion() * 100))
                                .collect(Collectors.joining(", ")));
            }

            // 检查磁盘上的标签数量
            for (int diskIdx = 0; diskIdx < GeneticParameters.NUM_DISKS; diskIdx++) {
                List<TagAllocation> diskTags = bestIndividualEver.getDisk(diskIdx);
                log.info("磁盘 " + diskIdx + " 包含 " + diskTags.size() + " 个标签");
            }
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
        for (int tagId = 0; tagId < GeneticParameters.TAGS.length; tagId++) {
            // 决定分割数量 (1到MAX_SPLITS_PER_TAG)
            int numSplits = random.nextInt(Math.min(GeneticParameters.MAX_SPLITS_PER_TAG,
                    GeneticParameters.NUM_DISKS)) + 1;

            // 随机选择磁盘
            List<Integer> diskIndices = getRandomSample(GeneticParameters.NUM_DISKS, numSplits);

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
                individual.addTagToDisk(tagId, diskIndices.get(i), proportions[i]);
            }
        }

        // 修复解决方案以满足约束条件
        repairIndividual(individual);

        return individual;
    }

    /**
     * 检查磁盘上的标签数量是否符合限制
     * 
     * @param individual 待检查的个体
     * @return 磁盘上的标签数量映射
     */
    private Map<Integer, Integer> checkDiskTagsCount(Individual individual) {
        Map<Integer, Integer> diskTagsCount = new HashMap<>();

        // 初始化计数
        for (int diskIdx = 0; diskIdx < GeneticParameters.NUM_DISKS; diskIdx++) {
            diskTagsCount.put(diskIdx, 0);
        }

        // 计算每个磁盘上的标签数量
        for (int tagId = 0; tagId < GeneticParameters.TAGS.length; tagId++) {
            for (Individual.DiskAllocation disk : individual.getTag(tagId)) {
                int diskIdx = disk.getDiskIdx();
                diskTagsCount.put(diskIdx, diskTagsCount.get(diskIdx) + 1);
            }
        }

        return diskTagsCount;
    }

    /**
     * 修复个体，确保满足约束条件
     * 
     * @param individual 待修复的个体
     */
    private void repairIndividual(Individual individual) {
        // 检查每个磁盘上的标签数量
        Map<Integer, Integer> diskTagsCount = checkDiskTagsCount(individual);

        // 检查每个标签的比例，移除低于最小阈值的分配
        for (int tagId = 0; tagId < GeneticParameters.TAGS.length; tagId++) {
            List<Individual.DiskAllocation> tagDisks = individual.getTag(tagId);
            boolean hasRemoved = false;

            // 移除比例过低的分配
            Iterator<Individual.DiskAllocation> iterator = tagDisks.iterator();
            while (iterator.hasNext()) {
                Individual.DiskAllocation disk = iterator.next();
                if (disk.getProportion() < GeneticParameters.MIN_TAG_PROPORTION) {
                    iterator.remove();
                    hasRemoved = true;

                    // 更新磁盘标签计数
                    int diskIdx = disk.getDiskIdx();
                    diskTagsCount.put(diskIdx, diskTagsCount.get(diskIdx) - 1);
                }
            }

            // 如果标签所有分配都被移除，创建一个新的随机分配
            if (tagDisks.isEmpty()) {
                int diskIdx = random.nextInt(GeneticParameters.NUM_DISKS);
                individual.addTagToDisk(tagId, diskIdx, 1.0);
                diskTagsCount.put(diskIdx, diskTagsCount.get(diskIdx) + 1);
            }
            // 如果有分配被移除，需要归一化剩余分配的比例
            else if (hasRemoved) {
                double totalProportion = tagDisks.stream()
                        .mapToDouble(Individual.DiskAllocation::getProportion)
                        .sum();

                for (Individual.DiskAllocation disk : tagDisks) {
                    disk.setProportion(disk.getProportion() / totalProportion);
                }
            }
        }

        // 修复超过上限的磁盘
        for (int diskIdx = 0; diskIdx < GeneticParameters.NUM_DISKS; diskIdx++) {
            int tagsCount = diskTagsCount.get(diskIdx);

            // 如果磁盘的标签数量超过上限，移除一些标签
            while (tagsCount > GeneticParameters.MAX_TAGS_PER_DISK) {
                // 获取该磁盘上的所有标签
                List<TagAllocation> diskTags = individual.getDisk(diskIdx);

                // 选择一个要移除的标签
                int tagToRemoveIdx = random.nextInt(diskTags.size());
                TagAllocation tagToRemove = diskTags.get(tagToRemoveIdx);
                int tagId = tagToRemove.getTagId();

                // 移除该标签在该磁盘上的分配
                removeTagFromDisk(individual, tagId, diskIdx);

                // 找一个标签数量少的磁盘来重新分配
                List<Integer> validDisks = new ArrayList<>();
                for (int i = 0; i < GeneticParameters.NUM_DISKS; i++) {
                    if (i != diskIdx && diskTagsCount.get(i) < GeneticParameters.MAX_TAGS_PER_DISK) {
                        validDisks.add(i);
                    }
                }

                if (!validDisks.isEmpty()) {
                    // 随机选择一个目标磁盘
                    int targetDisk = validDisks.get(random.nextInt(validDisks.size()));

                    // 检查标签的分割数量
                    if (individual.getTag(tagId).size() < GeneticParameters.MAX_SPLITS_PER_TAG) {
                        // 添加新的分配
                        individual.addTagToDisk(tagId, targetDisk, 0.1); // 暂时分配较小比例，稍后会归一化
                        diskTagsCount.put(targetDisk, diskTagsCount.get(targetDisk) + 1);
                    } else {
                        // 已经达到最大分割数，将比例转移到现有的分配中
                        redistributeTag(individual, tagId);
                    }
                } else {
                    // 没有可用的磁盘，只能重新分配到已有的磁盘
                    redistributeTag(individual, tagId);
                }

                // 更新计数
                tagsCount--;
                diskTagsCount.put(diskIdx, tagsCount);
            }
        }

        // 确保每个磁盘至少有一个标签
        for (int diskIdx = 0; diskIdx < GeneticParameters.NUM_DISKS; diskIdx++) {
            if (diskTagsCount.get(diskIdx) == 0) {
                // 寻找可以贡献标签的磁盘
                List<Integer> donorDisks = new ArrayList<>();
                for (int i = 0; i < GeneticParameters.NUM_DISKS; i++) {
                    if (diskTagsCount.get(i) > 1) { // 至少要有2个标签才能贡献
                        donorDisks.add(i);
                    }
                }

                if (!donorDisks.isEmpty()) {
                    // 随机选择一个捐赠磁盘
                    int donorDiskIdx = donorDisks.get(random.nextInt(donorDisks.size()));

                    // 找一个可以移动的标签
                    List<TagAllocation> donorTags = individual.getDisk(donorDiskIdx);
                    for (TagAllocation tag : donorTags) {
                        int tagId = tag.getTagId();

                        // 检查标签的分割数量
                        if (individual.getTag(tagId).size() < GeneticParameters.MAX_SPLITS_PER_TAG) {
                            // 添加到空磁盘
                            individual.addTagToDisk(tagId, diskIdx, 0.1); // 暂时分配较小比例，稍后会归一化

                            // 更新计数
                            diskTagsCount.put(diskIdx, diskTagsCount.get(diskIdx) + 1);
                            break;
                        }
                    }
                }
            }
        }

        // 归一化每个标签的比例
        individual.normalizeTagProportions();
    }

    /**
     * 从指定磁盘上移除标签
     * 
     * @param individual 个体
     * @param tagId      标签ID
     * @param diskIdx    磁盘索引
     */
    private void removeTagFromDisk(Individual individual, int tagId, int diskIdx) {
        List<Individual.DiskAllocation> tagDisks = individual.getTag(tagId);

        // 查找并移除对应的磁盘分配
        Iterator<Individual.DiskAllocation> iterator = tagDisks.iterator();
        while (iterator.hasNext()) {
            Individual.DiskAllocation disk = iterator.next();
            if (disk.getDiskIdx() == diskIdx) {
                iterator.remove();
                break;
            }
        }
    }

    /**
     * 重新分配标签的比例
     * 
     * @param individual 个体
     * @param tagId      标签ID
     */
    private void redistributeTag(Individual individual, int tagId) {
        // 获取标签的所有分配
        List<Individual.DiskAllocation> tagDisks = individual.getTag(tagId);

        // 如果已经没有分配，随机创建一个
        if (tagDisks.isEmpty()) {
            int targetDisk = random.nextInt(GeneticParameters.NUM_DISKS);
            individual.addTagToDisk(tagId, targetDisk, 1.0);
            return;
        }

        // 归一化剩余的分配
        double totalProportion = tagDisks.stream()
                .mapToDouble(Individual.DiskAllocation::getProportion)
                .sum();

        for (Individual.DiskAllocation disk : tagDisks) {
            disk.setProportion(disk.getProportion() / totalProportion);
        }
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
        if (random.nextDouble() < GeneticParameters.CROSSOVER_RATE) {
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

        for (int i = 0; i < GeneticParameters.TOURNAMENT_SIZE; i++) {
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

        // 以标签为单位进行交叉
        for (int tagId = 0; tagId < GeneticParameters.TAGS.length; tagId++) {
            // 随机选择一个父代的标签分配
            List<Individual.DiskAllocation> parentTag;
            if (random.nextBoolean()) {
                parentTag = parent1.getTag(tagId);
            } else {
                parentTag = parent2.getTag(tagId);
            }

            // 复制选中的标签分配
            for (Individual.DiskAllocation disk : parentTag) {
                child.addTagToDisk(tagId, disk.getDiskIdx(), disk.getProportion());
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
        // 随机选择一个标签进行变异
        int tagId = random.nextInt(GeneticParameters.TAGS.length);
        List<Individual.DiskAllocation> tagDisks = individual.getTag(tagId);

        // 随机选择变异类型
        double mutationType = random.nextDouble();

        if (mutationType < 0.33 && tagDisks.size() > 1) {
            // 变异类型1: 移除一个磁盘分配
            int diskIdxToRemove = random.nextInt(tagDisks.size());
            Individual.DiskAllocation diskToRemove = tagDisks.remove(diskIdxToRemove);

            // 将移除的比例重新分配给其他磁盘
            double removedProportion = diskToRemove.getProportion();
            double proportionPerDisk = removedProportion / (tagDisks.size());

            for (Individual.DiskAllocation disk : tagDisks) {
                disk.setProportion(disk.getProportion() + proportionPerDisk);
            }

        } else if (mutationType < 0.66 && tagDisks.size() < GeneticParameters.MAX_SPLITS_PER_TAG) {
            // 变异类型2: 添加一个新的磁盘分配

            // 找出所有没有这个标签的磁盘
            List<Integer> availableDisks = new ArrayList<>();
            Map<Integer, Integer> diskTagsCount = checkDiskTagsCount(individual);

            for (int diskIdx = 0; diskIdx < GeneticParameters.NUM_DISKS; diskIdx++) {
                boolean hasTagOnDisk = false;
                for (Individual.DiskAllocation disk : tagDisks) {
                    if (disk.getDiskIdx() == diskIdx) {
                        hasTagOnDisk = true;
                        break;
                    }
                }

                if (!hasTagOnDisk && diskTagsCount.get(diskIdx) < GeneticParameters.MAX_TAGS_PER_DISK) {
                    availableDisks.add(diskIdx);
                }
            }

            if (!availableDisks.isEmpty()) {
                // 随机选择一个磁盘
                int newDiskIdx = availableDisks.get(random.nextInt(availableDisks.size()));

                // 从现有分配中取出一些比例
                // 确保新分配的比例不小于最小阈值
                double newProportion = Math.max(GeneticParameters.MIN_TAG_PROPORTION, 0.2);

                // 减少其他磁盘的比例
                for (Individual.DiskAllocation disk : tagDisks) {
                    disk.setProportion(disk.getProportion() * (1 - newProportion));
                }

                // 添加新的磁盘分配
                individual.addTagToDisk(tagId, newDiskIdx, newProportion);
            }

        } else {
            // 变异类型3: 调整现有分配的比例
            if (tagDisks.size() >= 2) {
                // 随机选择两个磁盘
                int disk1Idx = random.nextInt(tagDisks.size());
                int disk2Idx;
                do {
                    disk2Idx = random.nextInt(tagDisks.size());
                } while (disk1Idx == disk2Idx);

                Individual.DiskAllocation disk1 = tagDisks.get(disk1Idx);
                Individual.DiskAllocation disk2 = tagDisks.get(disk2Idx);

                // 随机调整比例
                double shift = (random.nextDouble() * 0.2) * mutationRate; // 最多调整20%的比例
                double prop1 = disk1.getProportion();
                double prop2 = disk2.getProportion();

                // 确保调整后的比例不会小于最小阈值
                double minShift = Math.min(
                        prop1 - GeneticParameters.MIN_TAG_PROPORTION,
                        prop2 - GeneticParameters.MIN_TAG_PROPORTION);
                shift = Math.min(shift, minShift);

                // 如果可调整空间太小，就跳过这次变异
                if (shift <= 0.01) {
                    return;
                }

                // 随机决定调整方向
                if (random.nextBoolean()) {
                    disk1.setProportion(prop1 - shift);
                    disk2.setProportion(prop2 + shift);
                } else {
                    disk1.setProportion(prop1 + shift);
                    disk2.setProportion(prop2 - shift);
                }
            }
        }

        // 修复个体，确保符合约束
        repairIndividual(individual);
    }
}