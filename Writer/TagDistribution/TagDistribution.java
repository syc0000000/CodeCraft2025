package Writer.TagDistribution;

import java.util.*;
import Info.Info;
import Info.model.*;
import Writer.TagDistribution.DiskDistributor.Split;
import Writer.hardcode.dist1;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * 标签分布管理类，负责所有与标签相关的操作
 */
public class TagDistribution {
    private static final ModuleLogger log = LoggerFactory.getLogger("TagDistribution");

    // 聚类算法参数
    public static class ClusterConfig {
        // 聚类数量
        public static int K = 7;
        // 最大迭代次数
        public static int MAX_ITERATIONS = 100000;
        // 收敛阈值
        public static double CONVERGENCE_THRESHOLD = 1e-5;
        // 滑动窗口大小（用于平滑）
        public static int SMOOTH_WINDOW_SIZE = 1;
        // 是否使用DTW距离
        public static boolean USE_DTW = false;
        // DTW窗口大小
        public static int DTW_WINDOW = 3;
        // 是否使用Z-score标准化
        public static boolean USE_Z_SCORE = true;
        // 是否使用最小-最大标准化
        public static boolean USE_MIN_MAX = false;
        // 早停连续稳定次数
        public static int EARLY_STOP_PATIENCE = 500;
    }

    private static class TimeSeriesPoint {
        int tagId;
        double[] values;
        int clusterId;
        double[] originalValues; // 保存原始值，用于调试

        public TimeSeriesPoint(int tagId, double[] values) {
            this.tagId = tagId;
            this.values = values.clone();
            this.originalValues = values.clone();
            this.clusterId = -1;
        }
    }

    // Tag分布情况，key是tagId，value是分布情况（那个disk，分配比例）
    private Map<Integer, List<Split>> tagDistribution;
    // Tag聚类结果
    private ArrayList<ArrayList<Integer>> tagClusters;

    /**
     * 构造函数，初始化Tag分布和聚类
     */
    public TagDistribution() {
        int[] tagsMaxSize = new int[Info.tags.size()];
        for (int i = 0; i < Info.tags.size(); i++) {
            tagsMaxSize[i] = Info.tags.get(i).sizeMax;
        }

        // 使用遗传算法计算分布或使用硬编码分布
        // tagDistribution = DiskDistributor.computeDistributionByGA(tagsMaxSize);
        // 这里可以选择合适的分布策略
        // tagDistribution = DiskDistributor.createEvenDistribution(tagsMaxSize);
        tagDistribution = dist1.createHardcodedDistribution();

        // 初始化tag空间分布
        // distributeTagSpace();
        distributeTagSpaceWithSort();

        // 初始化tag聚类
        initializeTagClusters();
    }

    /**
     * 获取tag分布情况
     */
    public Map<Integer, List<Split>> getTagDistribution() {
        return tagDistribution;
    }

    /**
     * 获取tag聚类结果
     */
    public ArrayList<ArrayList<Integer>> getTagClusters() {
        return tagClusters;
    }

    /**
     * 给tag分空间
     */
    private void distributeTagSpace() {
        // 外层是disk，内层是tag
        ArrayList<ArrayList<TagSize>> sizes = new ArrayList<>();
        for (int i = 0; i < Info.diskNum; i++) {
            sizes.add(new ArrayList<>());
        }
        for (int tagId = 0; tagId < Info.tags.size(); tagId++) {
            Tag tag = Info.tags.get(tagId);
            List<Split> splits = tagDistribution.get(tagId);
            for (Split split : splits) {
                int diskId = split.diskIdx;
                double proportion = split.portion;
                int size = (int) (tag.sizeMax * proportion / 100);
                TagSize tagSize = new TagSize(tagId, size);
                sizes.get(diskId).add(tagSize);
                Info.tags.get(tagId).diskIdList.add(diskId);
            }
        }
        // 遍历disk
        for (int i = 0; i < Info.diskNum; i++) {
            LocalDisk disk = Info.localDiskTbl.get(i);
            ArrayList<TagSize> tagSizes = sizes.get(i);
            // 获取size总和
            int totalSize = 0;
            for (TagSize tagSize : tagSizes) {
                totalSize += tagSize.size;
            }
            // 根据tag的rwSize，缩放tagSize，比例
            double ratio = 1.0 * disk.logicalRWEnd / totalSize;
            for (TagSize tagSize : tagSizes) {
                tagSize.size = (int) (tagSize.size * ratio);
            }
            // 算left right，写tagMeta
            int left = 0;
            for (TagSize tagSize : tagSizes) {
                disk.tagMetas.add(new TagMeta(tagSize.tagId, left, left + tagSize.size - 1, -1, 0));
                // 建立初始空间
                DiskSpace diskSpace = new DiskSpace(true, left, left + tagSize.size - 1, disk.diskId, tagSize.tagId);
                for (int j = left; j < left + tagSize.size; j++) {
                    disk.unitData.get(j).space = diskSpace;
                }
                left += tagSize.size;
            }
            log.debug("disk " + disk.diskId + " 最终left: " + left + " logicalRWEnd: " + disk.logicalRWEnd);
        }
    }

    private void distributeTagSpaceWithSort() {
        // 外层是disk，内层是tag
        // 新建一个1,2,...16的HashSet<Integer> Instance
        HashSet<Integer> tagSet = new HashSet<>();
        for (int i = 0; i < Info.tags.size(); i++) {
            tagSet.add(i);
        }

        ArrayList<Integer> tagIdAfterSort = TimeWeightRanker.rankTags(tagSet);
        ArrayList<ArrayList<TagSize>> sizes = new ArrayList<>();
        for (int i = 0; i < Info.diskNum; i++) {
            sizes.add(new ArrayList<>());
        }

        for (int tagId : tagIdAfterSort) {
            Tag tag = Info.tags.get(tagId);
            List<Split> splits = tagDistribution.get(tagId);
            for (Split split : splits) {
                int diskId = split.diskIdx;
                double proportion = split.portion;
                int size = (int) (tag.sizeMax * proportion / 100);
                TagSize tagSize = new TagSize(tagId, size);
                sizes.get(diskId).add(tagSize);
                Info.tags.get(tagId).diskIdList.add(diskId);
            }
        }
        // 遍历disk
        for (int i = 0; i < Info.diskNum; i++) {
            LocalDisk disk = Info.localDiskTbl.get(i);
            ArrayList<TagSize> tagSizes = sizes.get(i);
            // 获取size总和
            int totalSize = 0;
            for (TagSize tagSize : tagSizes) {
                totalSize += tagSize.size;
            }
            // 根据tag的rwSize，缩放tagSize，比例
            double ratio = 1.0 * disk.logicalRWEnd / totalSize;
            for (TagSize tagSize : tagSizes) {
                tagSize.size = (int) (tagSize.size * ratio);
            }
            // 算left right，写tagMeta
            int left = 0;
            for (TagSize tagSize : tagSizes) {
                disk.tagMetas.add(new TagMeta(tagSize.tagId, left, left + tagSize.size - 1, -1, 0));
                // 建立初始空间
                DiskSpace diskSpace = new DiskSpace(true, left, left + tagSize.size - 1,
                        disk.diskId, tagSize.tagId);
                for (int j = left; j < left + tagSize.size; j++) {
                    disk.unitData.get(j).space = diskSpace;
                }
                left += tagSize.size;
            }
            log.debug("disk " + disk.diskId + " 最终left: " + left + " logicalRWEnd: "
                    + disk.logicalRWEnd);
        }
    }

    private class TagSize {
        public int tagId;
        public int size;

        public TagSize(int tagId, int size) {
            this.tagId = tagId;
            this.size = size;
        }
    }

    /**
     * 初始化标签聚类
     */
    private void initializeTagClusters() {
        try {
            // 构建时序数据点
            List<TimeSeriesPoint> points = new ArrayList<>();
            for (int i = 0; i < Info.tags.size(); i++) {
                double[] values = new double[48]; // 48个时间周期
                // 使用tag的read量作为时序特征
                ArrayList<Integer> readSizeByPeriod = Info.tags.get(i).readSizeByPeriod;
                for (int j = 0; j < 48; j++) {
                    values[j] = readSizeByPeriod.get(j);
                }
                points.add(new TimeSeriesPoint(i, values));
            }

            // 数据预处理
            preprocessData(points);

            // 使用成对组合方法进行聚类
            pairwiseClustering(points);

        } catch (Exception e) {
            log.error("初始化标签聚类失败: " + e.toString());
            throw new RuntimeException(e);
        }
    }

    /**
     * 成对组合聚类算法
     * 寻找最优的两两组合方式，使总的标准差最小
     */
    private void pairwiseClustering(List<TimeSeriesPoint> points) {
        int n = points.size();
        log.debug("开始成对组合聚类，共" + n + "个点");

        // 计算所有点对之间的距离
        double[][] distanceMatrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double distance = calculateDistance(points.get(i).values, points.get(j).values);
                distanceMatrix[i][j] = distance;
                distanceMatrix[j][i] = distance;
            }
        }

        // 定义要找的最佳组合方案
        List<Pair> bestPairs = null;
        double minTotalStd = Double.MAX_VALUE;

        // 使用贪心算法寻找较好的组合方案
        for (int iter = 0; iter < 100; iter++) { // 尝试多次以获得较好结果
            boolean[] used = new boolean[n];
            List<Pair> currentPairs = new ArrayList<>();

            // 随机选择起始点
            Random rand = new Random();
            int startIdx = rand.nextInt(n);

            while (true) {
                // 找到当前未使用的点
                List<Integer> unusedIndices = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if (!used[i]) {
                        unusedIndices.add(i);
                    }
                }

                if (unusedIndices.size() <= 1) {
                    break; // 没有更多可组合的点
                }

                // 找到最佳匹配
                int bestI = -1;
                int bestJ = -1;
                double minDist = Double.MAX_VALUE;

                for (int i = 0; i < unusedIndices.size(); i++) {
                    for (int j = i + 1; j < unusedIndices.size(); j++) {
                        int idxI = unusedIndices.get(i);
                        int idxJ = unusedIndices.get(j);
                        if (distanceMatrix[idxI][idxJ] < minDist) {
                            minDist = distanceMatrix[idxI][idxJ];
                            bestI = idxI;
                            bestJ = idxJ;
                        }
                    }
                }

                if (bestI != -1 && bestJ != -1) {
                    currentPairs.add(new Pair(bestI, bestJ));
                    used[bestI] = true;
                    used[bestJ] = true;
                } else {
                    break;
                }
            }

            // 处理剩余的单个点
            List<Integer> remainingPoints = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!used[i]) {
                    remainingPoints.add(i);
                }
            }

            // 如果有剩余的点，将它们分配到最近的对中
            for (int idx : remainingPoints) {
                if (currentPairs.isEmpty()) {
                    // 如果没有对，创建一个只包含该点的"对"
                    currentPairs.add(new Pair(idx, -1));
                    continue;
                }

                // 寻找最佳匹配对
                Pair bestPair = null;
                double minPairDist = Double.MAX_VALUE;

                for (Pair pair : currentPairs) {
                    if (pair.second == -1) {
                        // 如果是单点对，直接匹配
                        double dist = distanceMatrix[idx][pair.first];
                        if (dist < minPairDist) {
                            minPairDist = dist;
                            bestPair = pair;
                        }
                    } else {
                        // 计算到对中两点的平均距离
                        double avgDist = (distanceMatrix[idx][pair.first] + distanceMatrix[idx][pair.second]) / 2;
                        if (avgDist < minPairDist) {
                            minPairDist = avgDist;
                            bestPair = pair;
                        }
                    }
                }

                if (bestPair != null) {
                    if (bestPair.second == -1) {
                        // 如果是单点对，直接将该点加入
                        bestPair.second = idx;
                    } else {
                        // 否则创建一个新的包含该点的对
                        currentPairs.add(new Pair(idx, -1));
                    }
                }
            }

            // 计算当前组合的总标准差
            double currentTotalStd = calculateTotalStandardDeviation(points, currentPairs);

            // 更新最佳组合
            if (currentTotalStd < minTotalStd) {
                minTotalStd = currentTotalStd;
                bestPairs = new ArrayList<>(currentPairs);
                log.debug("发现更好的组合方案，总标准差: " + minTotalStd);
            }
        }

        // 构建最终的聚类结果
        buildFinalClustersFromPairs(points, bestPairs);
    }

    /**
     * 表示一对点的类
     */
    private class Pair {
        int first;
        int second;

        public Pair(int first, int second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * 计算给定组合的总标准差
     */
    private double calculateTotalStandardDeviation(List<TimeSeriesPoint> points, List<Pair> pairs) {
        double totalStd = 0;

        for (Pair pair : pairs) {
            if (pair.second == -1) {
                // 单点对没有标准差
                continue;
            }

            double[] values1 = points.get(pair.first).values;
            double[] values2 = points.get(pair.second).values;

            // 计算每个时间点的标准差
            for (int t = 0; t < values1.length; t++) {
                double mean = (values1[t] + values2[t]) / 2;
                double variance = Math.pow(values1[t] - mean, 2) + Math.pow(values2[t] - mean, 2);
                totalStd += Math.sqrt(variance / 2);
            }
        }

        return totalStd;
    }

    /**
     * 从对组合构建最终聚类
     */
    private void buildFinalClustersFromPairs(List<TimeSeriesPoint> points, List<Pair> pairs) {
        log.debug("从" + pairs.size() + "对组合构建最终聚类");
        tagClusters = new ArrayList<>();

        // 为每个对创建一个聚类
        for (Pair pair : pairs) {
            ArrayList<Integer> cluster = new ArrayList<>();

            int tagId1 = points.get(pair.first).tagId;
            cluster.add(tagId1);
            points.get(pair.first).clusterId = tagClusters.size();

            if (pair.second != -1) {
                int tagId2 = points.get(pair.second).tagId;
                cluster.add(tagId2);
                points.get(pair.second).clusterId = tagClusters.size();
            }

            tagClusters.add(cluster);
        }

        log.debug("聚类完成，共" + tagClusters.size() + "个聚类");
        for (int i = 0; i < tagClusters.size(); i++) {
            log.debug("聚类 " + i + ": " + tagClusters.get(i));
        }
    }

    private void preprocessData(List<TimeSeriesPoint> points) {
        if (points.isEmpty())
            return;
        int n = points.get(0).values.length;

        if (ClusterConfig.USE_Z_SCORE) {
            // Z-Score标准化
            double[] means = new double[n];
            double[] stds = new double[n];

            // 计算均值和标准差
            for (int i = 0; i < n; i++) {
                double sum = 0;
                for (TimeSeriesPoint p : points) {
                    sum += p.values[i];
                }
                means[i] = sum / points.size();

                double var = 0;
                for (TimeSeriesPoint p : points) {
                    var += Math.pow(p.values[i] - means[i], 2);
                }
                stds[i] = Math.sqrt(var / points.size());
            }

            // 应用Z-Score标准化
            for (TimeSeriesPoint p : points) {
                for (int i = 0; i < n; i++) {
                    if (stds[i] > 0) {
                        p.values[i] = (p.values[i] - means[i]) / stds[i];
                    }
                }
            }
        } else if (ClusterConfig.USE_MIN_MAX) {
            // 最小-最大标准化
            for (int i = 0; i < n; i++) {
                double min = Double.MAX_VALUE;
                double max = Double.MIN_VALUE;
                for (TimeSeriesPoint p : points) {
                    min = Math.min(min, p.values[i]);
                    max = Math.max(max, p.values[i]);
                }
                if (max > min) {
                    for (TimeSeriesPoint p : points) {
                        p.values[i] = (p.values[i] - min) / (max - min);
                    }
                }
            }
        }

        // 滑动窗口平滑
        int windowSize = ClusterConfig.SMOOTH_WINDOW_SIZE;
        if (windowSize > 1) {
            for (TimeSeriesPoint p : points) {
                double[] smoothed = new double[n];
                System.arraycopy(p.values, 0, smoothed, 0, n);

                for (int i = windowSize / 2; i < n - windowSize / 2; i++) {
                    double sum = 0;
                    for (int j = -windowSize / 2; j <= windowSize / 2; j++) {
                        sum += p.values[i + j];
                    }
                    smoothed[i] = sum / windowSize;
                }
                p.values = smoothed;
            }
        }
    }

    private double calculateDistance(double[] a, double[] b) {
        if (ClusterConfig.USE_DTW) {
            return dtwDistance(a, b);
        } else {
            return trendDistance(a, b);
        }
    }

    private double trendDistance(double[] a, double[] b) {
        // 计算一阶差分趋势差异
        double diffSum = 0;
        for (int i = 0; i < 47; i++) {
            double diffA = a[i + 1] - a[i];
            double diffB = b[i + 1] - b[i];
            diffSum += Math.abs(diffA - diffB);
        }
        return diffSum;
    }

    private double dtwDistance(double[] a, double[] b) {
        int n = a.length;
        double[][] dp = new double[n][n];

        // 初始化
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                dp[i][j] = Double.MAX_VALUE;
            }
        }
        dp[0][0] = Math.abs(a[0] - b[0]);

        // 动态规划填表
        for (int i = 0; i < n; i++) {
            for (int j = Math.max(0, i - ClusterConfig.DTW_WINDOW); j < Math.min(n,
                    i + ClusterConfig.DTW_WINDOW + 1); j++) {
                if (i == 0 && j == 0)
                    continue;

                double cost = Math.abs(a[i] - b[j]);
                double minPrev = Double.MAX_VALUE;

                if (i > 0)
                    minPrev = Math.min(minPrev, dp[i - 1][j]);
                if (j > 0)
                    minPrev = Math.min(minPrev, dp[i][j - 1]);
                if (i > 0 && j > 0)
                    minPrev = Math.min(minPrev, dp[i - 1][j - 1]);

                dp[i][j] = cost + minPrev;
            }
        }

        return dp[n - 1][n - 1];
    }

    private void fallbackClustering() {
        log.debug("fallbackClustering");
        // 简单的均匀分配策略
        tagClusters = new ArrayList<>();
        for (int i = 0; i < ClusterConfig.K; i++) {
            tagClusters.add(new ArrayList<>());
        }
        for (int i = 0; i < Info.tags.size(); i++) {
            tagClusters.get(i % ClusterConfig.K).add(i);
        }
    }

    /**
     * 获取指定tag的相似tag列表
     * 
     * @param tagId 标签ID
     * @return 相似标签ID列表
     */
    public ArrayList<Integer> getSimilarTags(int tagId) {
        for (ArrayList<Integer> cluster : tagClusters) {
            if (cluster.contains(tagId)) {
                return cluster;
            }
        }
        // 如果没有找到，返回一个只包含自己的列表
        ArrayList<Integer> selfList = new ArrayList<>();
        selfList.add(tagId);
        return selfList;
    }
}