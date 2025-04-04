package Writer.TagDistribution;

import java.util.*;
import Info.Info;
import Info.model.*;
import Writer.TagDistribution.DiskDistributor.Split;

/**
 * 标签分布管理类，负责所有与标签相关的操作
 */
public class TagDistribution {
    // 聚类算法参数
    public static class ClusterConfig {
        // 聚类数量
        public static int K = 7;
        // 最大迭代次数
        public static int MAX_ITERATIONS = 100;
        // 收敛阈值
        public static double CONVERGENCE_THRESHOLD = 1e-4;
        // 滑动窗口大小（用于平滑）
        public static int SMOOTH_WINDOW_SIZE = 3;
        // 是否使用DTW距离
        public static boolean USE_DTW = false;
        // DTW窗口大小
        public static int DTW_WINDOW = 3;
        // 是否使用Z-score标准化
        public static boolean USE_Z_SCORE = true;
        // 是否使用最小-最大标准化
        public static boolean USE_MIN_MAX = false;
        // 早停连续稳定次数
        public static int EARLY_STOP_PATIENCE = 3;
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
        tagDistribution = DiskDistributor.createEvenDistribution(tagsMaxSize);

        // 初始化tag空间分布
        distributeTagSpace();

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
                disk.tagMetas.add(new TagMeta(tagSize.tagId, left, left + tagSize.size - 1, 0));
                left += tagSize.size;
            }
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

            // 初始化聚类中心
            List<TimeSeriesPoint> centroids = initializeCentroids(points);

            // 用于早停的变量
            int stabilityCounter = 0;
            double lastTotalDistance = Double.MAX_VALUE;

            // 迭代聚类
            for (int iter = 0; iter < ClusterConfig.MAX_ITERATIONS; iter++) {
                // 分配点到最近的聚类中心
                double totalDistance = assignPointsToClusters(points, centroids);

                // 更新聚类中心
                List<TimeSeriesPoint> newCentroids = updateCentroids(points);

                // 检查收敛
                boolean converged = checkConvergence(centroids, newCentroids);

                // 早停检查
                double improvement = Math.abs(lastTotalDistance - totalDistance) / lastTotalDistance;
                if (improvement < ClusterConfig.CONVERGENCE_THRESHOLD) {
                    stabilityCounter++;
                    if (stabilityCounter >= ClusterConfig.EARLY_STOP_PATIENCE) {
                        break;
                    }
                } else {
                    stabilityCounter = 0;
                }
                lastTotalDistance = totalDistance;

                if (converged)
                    break;
                centroids = newCentroids;
            }

            // 构建最终结果
            buildFinalClusters(points);

        } catch (Exception e) {
            // 如果聚类失败，使用简单的均匀分配
            fallbackClustering();
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

    private List<TimeSeriesPoint> initializeCentroids(List<TimeSeriesPoint> points) {
        List<TimeSeriesPoint> centroids = new ArrayList<>();
        Random rand = new Random();

        // K-means++初始化
        centroids.add(new TimeSeriesPoint(-1, points.get(rand.nextInt(points.size())).values.clone()));

        while (centroids.size() < ClusterConfig.K) {
            double[] distances = new double[points.size()];
            double total = 0;

            for (int i = 0; i < points.size(); i++) {
                double minDist = Double.MAX_VALUE;
                for (TimeSeriesPoint c : centroids) {
                    double d = calculateDistance(points.get(i).values, c.values);
                    if (d < minDist)
                        minDist = d;
                }
                distances[i] = minDist * minDist; // 平方距离增加分散性
                total += distances[i];
            }

            double r = rand.nextDouble() * total;
            double sum = 0;
            for (int i = 0; i < points.size(); i++) {
                sum += distances[i];
                if (sum >= r) {
                    centroids.add(new TimeSeriesPoint(-1, points.get(i).values.clone()));
                    break;
                }
            }
        }

        return centroids;
    }

    private double assignPointsToClusters(List<TimeSeriesPoint> points, List<TimeSeriesPoint> centroids) {
        double totalDistance = 0;
        for (TimeSeriesPoint p : points) {
            double minDist = Double.MAX_VALUE;
            int closest = -1;

            for (int i = 0; i < centroids.size(); i++) {
                double d = calculateDistance(p.values, centroids.get(i).values);
                if (d < minDist) {
                    minDist = d;
                    closest = i;
                }
            }
            p.clusterId = closest;
            totalDistance += minDist;
        }
        return totalDistance;
    }

    private List<TimeSeriesPoint> updateCentroids(List<TimeSeriesPoint> points) {
        List<TimeSeriesPoint> newCentroids = new ArrayList<>();
        for (int i = 0; i < ClusterConfig.K; i++) {
            double[] centroidValues = new double[48];
            int count = 0;

            for (TimeSeriesPoint p : points) {
                if (p.clusterId == i) {
                    for (int j = 0; j < 48; j++) {
                        centroidValues[j] += p.values[j];
                    }
                    count++;
                }
            }

            if (count > 0) {
                for (int j = 0; j < 48; j++)
                    centroidValues[j] /= count;
                newCentroids.add(new TimeSeriesPoint(i, centroidValues));
            } else {
                // 如果没有点分配到这个中心，随机选择一个点
                TimeSeriesPoint randomPoint = points.get(new Random().nextInt(points.size()));
                newCentroids.add(new TimeSeriesPoint(i, randomPoint.values.clone()));
            }
        }
        return newCentroids;
    }

    private boolean checkConvergence(List<TimeSeriesPoint> oldCentroids, List<TimeSeriesPoint> newCentroids) {
        for (int i = 0; i < ClusterConfig.K; i++) {
            if (calculateDistance(oldCentroids.get(i).values,
                    newCentroids.get(i).values) > ClusterConfig.CONVERGENCE_THRESHOLD) {
                return false;
            }
        }
        return true;
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

    private void buildFinalClusters(List<TimeSeriesPoint> points) {
        tagClusters = new ArrayList<>();
        for (int i = 0; i < ClusterConfig.K; i++) {
            tagClusters.add(new ArrayList<>());
        }
        for (TimeSeriesPoint p : points) {
            tagClusters.get(p.clusterId).add(p.tagId);
        }
    }

    private void fallbackClustering() {
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