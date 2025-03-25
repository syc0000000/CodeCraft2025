package IO.model;

import java.text.DecimalFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DiskDistributionMT {

    // 定义 tag 数组
    private static int[] tags = { 1968, 799, 2121, 1849, 944, 502, 1933, 1507, 494, 1540, 631,
            1715, 434, 1107, 545, 2196 };
    // 分配方案: tag 索引 -> 若干拆分
    private static final Map<Integer, List<Split>> globalBestDistribution = new HashMap<>();
    private static final Lock mtx = new ReentrantLock();
    private static double globalBestVariance = Double.POSITIVE_INFINITY;

    // 定义分配方案的数据结构
    public static class Split {
        public int diskIdx;
        public int portion; // 拆分的百分比

        public Split(int d, int p) {
            this.diskIdx = d;
            this.portion = p;
        }

        @Override
        public String toString() {
            return "Split 磁盘ID：" + diskIdx + ", 百分比: " + portion + "%";
        }
    }

    /**
     * Main调用这个方法，传入tagValues
     * 
     * @param tagValues
     * @return
     */
    public static Map<Integer, List<Split>> entrypoint(int[] tagValues) {
        // Set the tags array with the input values
        tags = tagValues;

        Map.Entry<Map<Integer, List<Split>>, Double> result = findOptimalDistributionMT();
        Map<Integer, List<Split>> bestDistribution = result.getKey();

        return bestDistribution;
    }

    /**
     * 本地测试用，默认使用sample_practice.txt处理后拿到的数据
     * 
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("使用 " + Runtime.getRuntime().availableProcessors() + " 个线程进行计算");
        long startTime = System.currentTimeMillis();

        Map.Entry<Map<Integer, List<Split>>, Double> result = findOptimalDistributionMT();
        Map<Integer, List<Split>> bestDistribution = result.getKey();
        double bestVariance = result.getValue();

        long endTime = System.currentTimeMillis();
        printResult(bestDistribution);

        System.out.println("\n程序运行时间: " + (endTime - startTime) + " 毫秒");
    }

    // 计算磁盘负载
    public static List<Double> calculateDiskLoads(Map<Integer, List<Split>> distribution) {
        List<Double> diskLoads = new ArrayList<>(Collections.nCopies(10, 0.0));
        for (Map.Entry<Integer, List<Split>> entry : distribution.entrySet()) {
            double tagSize = tags[entry.getKey()];
            for (Split s : entry.getValue()) {
                diskLoads.set(s.diskIdx, diskLoads.get(s.diskIdx) + tagSize * s.portion / 100.0);
            }
        }
        return diskLoads;
    }

    // 检查分配是否有效
    public static boolean isValidDistribution(Map<Integer, List<Split>> distribution) {
        int[] diskTags = new int[10];
        Arrays.fill(diskTags, 0);

        for (Map.Entry<Integer, List<Split>> entry : distribution.entrySet()) {
            int totalPortion = 0;
            for (Split s : entry.getValue()) {
                if (s.diskIdx >= 10) {
                    return false;
                }
                diskTags[s.diskIdx]++;
                totalPortion += s.portion;
            }
            if (totalPortion != 100) {
                return false;
            }
        }

        for (int count : diskTags) {
            if (count < 1 || count > 4) {
                return false;
            }
        }
        return true;
    }

    // 计算评分（考虑多个因素）
    public static double calculateScore(List<Double> diskLoads) {
        double totalLoad = 0.0;
        double minLoad = Double.POSITIVE_INFINITY;
        double maxLoad = 0.0;

        for (double load : diskLoads) {
            totalLoad += load;
            if (load < minLoad)
                minLoad = load;
            if (load > maxLoad)
                maxLoad = load;
        }

        double mean = totalLoad / 10.0;
        double variance = 0.0;
        double maxDeviation = 0.0;

        for (double load : diskLoads) {
            double diff = load - mean;
            variance += diff * diff;
            double absDiff = Math.abs(diff);
            if (absDiff > maxDeviation)
                maxDeviation = absDiff;
        }

        // 调整权重以更注重均衡性
        return variance / 10.0 + maxDeviation * 3.0 + (maxLoad - minLoad) * 2.0;
    }

    // 生成所有可能的分割方案
    public static List<List<Integer>> generateAllPortions(int numSplits) {
        List<List<Integer>> result = new ArrayList<>();
        List<Integer> current = new ArrayList<>();

        class Helper {
            void generateRecursive(List<Integer> curr, int remaining, int partsLeft, int minVal) {
                if (partsLeft == 1) {
                    if (remaining >= minVal) {
                        curr.add(remaining);
                        result.add(new ArrayList<>(curr));
                        curr.remove(curr.size() - 1);
                    }
                    return;
                }
                for (int i = minVal; i <= remaining - minVal * (partsLeft - 1); i++) {
                    curr.add(i);
                    generateRecursive(curr, remaining - i, partsLeft - 1, minVal);
                    curr.remove(curr.size() - 1);
                }
            }
        }

        new Helper().generateRecursive(current, 100, numSplits, 1);
        return result;
    }

    // 模拟退火算法的温度函数
    public static double temperature(int iteration, int maxIterations) {
        return Math.pow(0.99, iteration * (double) maxIterations / 1000000.0);
    }

    // 初始化一个基本的分配方案
    public static Map<Integer, List<Split>> initializeDistribution() {
        Map<Integer, List<Split>> dist = new HashMap<>();
        List<Double> diskLoads = new ArrayList<>(Collections.nCopies(10, 0.0));
        int[] diskTagCounts = new int[10];
        Arrays.fill(diskTagCounts, 0);

        // 按大小排序 tags
        List<int[]> sortedTags = new ArrayList<>();
        for (int i = 0; i < tags.length; i++) {
            sortedTags.add(new int[] { tags[i], i });
        }
        // 降序排序
        sortedTags.sort((a, b) -> Integer.compare(b[0], a[0]));

        // 贪心分配
        for (int[] pair : sortedTags) {
            int size = pair[0];
            int tagIdx = pair[1];
            int bestDisk = -1;
            double minScore = Double.POSITIVE_INFINITY;

            for (int disk = 0; disk < 10; disk++) {
                if (diskTagCounts[disk] >= 4)
                    continue;

                double oldVal = diskLoads.get(disk);
                diskLoads.set(disk, oldVal + size);
                double score = calculateScore(diskLoads);
                diskLoads.set(disk, oldVal);

                if (score < minScore) {
                    minScore = score;
                    bestDisk = disk;
                }
            }
            List<Split> splits = new ArrayList<>();
            splits.add(new Split(bestDisk, 100));
            dist.put(tagIdx, splits);

            diskLoads.set(bestDisk, diskLoads.get(bestDisk) + size);
            diskTagCounts[bestDisk]++;
        }
        return dist;
    }

    // 尝试优化分配方案
    public static void optimizeDistribution(int threadId, int numThreads) {
        Random gen = new Random();
        // 预生成所有可能的分割方案
        List<List<List<Integer>>> allPortions = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            allPortions.add(generateAllPortions(i));
        }

        Map<Integer, List<Split>> currentDist = initializeDistribution();
        double localBestScore = calculateScore(calculateDiskLoads(currentDist));
        Map<Integer, List<Split>> localBestDist = copyDistribution(currentDist);

        int stagnationCount = 0;
        int iterationsPerThread = 100000;
        int tagsSize = tags.length;

        for (int iter = 0; iter < iterationsPerThread; iter++) {
            double temp = temperature(iter, iterationsPerThread);
            Map<Integer, List<Split>> newDist = copyDistribution(currentDist);

            int numTagsToOptimize = (iter % 4) + 1;
            Set<Integer> selectedTags = new HashSet<>();
            for (int i = 0; i < numTagsToOptimize; i++) {
                int tagIdx;
                do {
                    tagIdx = gen.nextInt(tagsSize);
                } while (selectedTags.contains(tagIdx));
                selectedTags.add(tagIdx);
            }

            for (int tagIdx : selectedTags) {
                List<Split> splits = newDist.get(tagIdx);
                // 随机选择分割数量
                int numSplits = gen.nextInt(4) + 1;
                List<List<Integer>> portions = allPortions.get(numSplits - 1);
                if (portions.isEmpty())
                    continue;

                List<Integer> selectedPortions = portions.get(gen.nextInt(portions.size()));
                splits.clear();

                List<Integer> availableDisks = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    availableDisks.add(i);
                }
                Collections.shuffle(availableDisks, gen);

                for (int i = 0; i < numSplits; i++) {
                    splits.add(new Split(availableDisks.get(i), selectedPortions.get(i)));
                }
            }

            if (isValidDistribution(newDist)) {
                double newScore = calculateScore(calculateDiskLoads(newDist));
                double currentScore = calculateScore(calculateDiskLoads(currentDist));
                double delta = newScore - currentScore;

                if (delta < 0 || (temp > 0 && Math.exp(-delta / temp) > gen.nextDouble())) {
                    currentDist = newDist;
                    currentScore = newScore;

                    if (newScore < localBestScore) {
                        localBestScore = newScore;
                        localBestDist = copyDistribution(newDist);
                        stagnationCount = 0;
                    }

                    mtx.lock();
                    try {
                        if (newScore < globalBestVariance) {
                            globalBestVariance = newScore;
                            copyIntoGlobalBest(newDist);
                        }
                    } finally {
                        mtx.unlock();
                    }
                }
            }

            stagnationCount++;
            if (stagnationCount > 10000) {
                currentDist = initializeDistribution();
                stagnationCount = 0;
            }
        }
    }

    // 多线程优化
    public static Map.Entry<Map<Integer, List<Split>>, Double> findOptimalDistributionMT() {
        int numThreads = Runtime.getRuntime().availableProcessors();
        List<Thread> threads = new ArrayList<>();

        Map<Integer, List<Split>> initDist = initializeDistribution();
        globalBestVariance = calculateScore(calculateDiskLoads(initDist));
        copyIntoGlobalBest(initDist);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            Thread t = new Thread(() -> optimizeDistribution(threadId, numThreads));
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return new AbstractMap.SimpleEntry<>(copyDistribution(globalBestDistribution),
                globalBestVariance);
    }

    // 打印结果
    private static void printResult(Map<Integer, List<Split>> distribution) {
        if (distribution.isEmpty()) {
            System.out.println("未找到有效解");
            return;
        }

        List<Double> diskLoads = calculateDiskLoads(distribution);
        List<List<int[]>> diskTags = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            diskTags.add(new ArrayList<>());
        }

        for (Map.Entry<Integer, List<Split>> entry : distribution.entrySet()) {
            int tagIdx = entry.getKey();
            for (Split s : entry.getValue()) {
                double portion = s.portion / 100.0;
                diskTags.get(s.diskIdx).add(new int[] { tagIdx, (int) (portion * tags[tagIdx]) });
            }
        }

        double totalLoad = 0.0;
        double minLoad = Double.POSITIVE_INFINITY;
        double maxLoad = 0.0;
        for (double load : diskLoads) {
            totalLoad += load;
            if (load < minLoad)
                minLoad = load;
            if (load > maxLoad)
                maxLoad = load;
        }

        double meanLoad = totalLoad / 10.0;
        double variance = 0.0;
        for (double load : diskLoads) {
            double diff = load - meanLoad;
            variance += diff * diff;
        }
        variance /= 10.0;

        DecimalFormat df = new DecimalFormat("0.00");
        System.out.println("\n负载统计：");
        System.out.println("平均负载: " + df.format(meanLoad));
        System.out.println("最小负载: " + minLoad);
        System.out.println("最大负载: " + maxLoad);
        System.out.println("负载差异: " + (maxLoad - minLoad));
        System.out.println("方差: " + variance);
        System.out.println("优化目标值: " + globalBestVariance);

        System.out.println("\n最优分配方案：");
        for (int diskIdx = 0; diskIdx < 10; diskIdx++) {
            System.out.println("\n磁盘 " + diskIdx + ":");
            System.out.println("总负载: " + df.format(diskLoads.get(diskIdx)));
            System.out.println("Tags:");
            for (int[] info : diskTags.get(diskIdx)) {
                int tagIdx = info[0];
                double portionLoad = info[1];
                double originalTag = tags[tagIdx];
                double fraction = portionLoad / originalTag;
                System.out.println("  Tag " + tagIdx + ": " + df.format(fraction) + " * "
                        + (int) originalTag + " = " + df.format(portionLoad));
            }
        }
    }

    private static Map<Integer, List<Split>> copyDistribution(Map<Integer, List<Split>> source) {
        Map<Integer, List<Split>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<Split>> e : source.entrySet()) {
            List<Split> newSplits = new ArrayList<>();
            for (Split s : e.getValue()) {
                newSplits.add(new Split(s.diskIdx, s.portion));
            }
            copy.put(e.getKey(), newSplits);
        }
        return copy;
    }

    private static void copyIntoGlobalBest(Map<Integer, List<Split>> source) {
        globalBestDistribution.clear();
        for (Map.Entry<Integer, List<Split>> e : source.entrySet()) {
            List<Split> newSplits = new ArrayList<>();
            for (Split s : e.getValue()) {
                newSplits.add(new Split(s.diskIdx, s.portion));
            }
            globalBestDistribution.put(e.getKey(), newSplits);
        }
    }
}
