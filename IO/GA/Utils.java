package IO.GA;

import java.util.Arrays;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * 工具类，提供各种计算函数
 */
public class Utils {

    private static final ModuleLogger log = LoggerFactory.getLogger("DiskGA");

    /**
     * 计算均值
     * 
     * @param array 数组
     * @return 均值
     */
    public static double mean(double[] array) {
        return Arrays.stream(array).average().orElse(0);
    }

    /**
     * 计算标准差
     * 
     * @param array 数组
     * @return 标准差
     */
    public static double standardDeviation(double[] array) {
        double mean = mean(array);
        double sum = 0;

        for (double value : array) {
            sum += Math.pow(value - mean, 2);
        }

        return Math.sqrt(sum / array.length);
    }

    /**
     * 计算每个磁盘的对象数量
     * 
     * @param individual 个体
     * @return 每个磁盘的对象数量数组
     */
    public static double[] calculateDiskLoads(Individual individual) {
        double[] diskLoads = new double[GeneticParameters.NUM_DISKS];

        // 对于每个标签，计算它在各个磁盘上的分布
        for (int tagId = 0; tagId < GeneticParameters.TAGS.length; tagId++) {
            double tagSize = GeneticParameters.TAGS[tagId];

            // 获取标签在各个磁盘上的分配
            for (Individual.DiskAllocation disk : individual.getTag(tagId)) {
                int diskIdx = disk.getDiskIdx();
                double proportion = disk.getProportion();
                diskLoads[diskIdx] += tagSize * proportion;
            }
        }

        return diskLoads;
    }

    /**
     * 计算每个时间段每个磁盘的负载量
     * 使用标签在每个时间段的存在数量计算
     * 
     * @param individual 个体
     * @return 负载量矩阵，形状为[时间段数][磁盘数]
     */
    public static double[][] calculateDiskReadsPerTimepoint(Individual individual) {
        int numTimepoints = GeneticParameters.TAGS_PER_TIMEPOINT.size();
        double[][] diskReads = new double[numTimepoints][GeneticParameters.NUM_DISKS];

        // 对于每个标签，计算它在各个时间点各个磁盘上的分布
        for (int tagId = 0; tagId < GeneticParameters.TAGS.length; tagId++) {
            // 获取标签在各个磁盘上的分配
            for (Individual.DiskAllocation disk : individual.getTag(tagId)) {
                int diskIdx = disk.getDiskIdx();
                double proportion = disk.getProportion();

                // 计算每个时间点的读取量
                for (int timeIdx = 0; timeIdx < numTimepoints; timeIdx++) {
                    if (tagId < GeneticParameters.TAGS_PER_TIMEPOINT.get(timeIdx).size()) {
                        int tagValue = GeneticParameters.TAGS_PER_TIMEPOINT.get(timeIdx).get(tagId);
                        diskReads[timeIdx][diskIdx] += tagValue * proportion;
                    } else {
                        log.error("时间点 " + timeIdx + " 中缺少标签 " + tagId);
                    }
                }
            }
        }

        return diskReads;
    }

    /**
     * 计算每个磁盘在不同时间段的对象数量方差
     * 方差越小表示磁盘负载在时间段内越稳定
     * 
     * @param individual 个体
     * @return 每个磁盘的负载方差数组
     */
    public static double[] calculateDiskReadVariance(Individual individual) {
        double[][] diskReads = calculateDiskReadsPerTimepoint(individual);
        double[] variances = new double[GeneticParameters.NUM_DISKS];

        for (int diskIdx = 0; diskIdx < GeneticParameters.NUM_DISKS; diskIdx++) {
            double[] readsForDisk = new double[diskReads.length];
            for (int timeIdx = 0; timeIdx < diskReads.length; timeIdx++) {
                readsForDisk[timeIdx] = diskReads[timeIdx][diskIdx];
            }

            // 计算方差
            double mean = mean(readsForDisk);
            double sumSquaredDiff = 0;
            for (double read : readsForDisk) {
                sumSquaredDiff += Math.pow(read - mean, 2);
            }
            variances[diskIdx] = sumSquaredDiff / readsForDisk.length;
        }

        return variances;
    }

    /**
     * 评估个体的适应度
     * 适应度由以下几个因素组成：
     * 1. 对象分布的变异系数 (40%)
     * 2. 磁盘读取方差的均值 (30%)
     * 3. 负载范围归一化值 (20%)
     * 4. 方差范围 (10%)
     * 
     * @param individual 个体
     * @return 适应度分数（越低越好）
     */
    public static double evaluateFitness(Individual individual) {
        // 计算对象负载
        double[] diskLoads = calculateDiskLoads(individual);

        // 负载均衡性，使用变异系数代替标准差，以避免大小标签的影响
        double loadMean = mean(diskLoads);
        double loadStd = standardDeviation(diskLoads);
        double loadCv = loadMean > 0 ? loadStd / loadMean : Double.POSITIVE_INFINITY;

        // 计算每个磁盘在所有时间点的读取量方差
        double[] diskReadVariances = calculateDiskReadVariance(individual);

        // 读取方差均值
        double readVarianceMean = mean(diskReadVariances);

        // 负载最大差异（最大值和最小值之间的差异）
        double loadRange = Arrays.stream(diskLoads).max().getAsDouble() - Arrays.stream(diskLoads).min().getAsDouble();
        double loadRangeNormalized = loadMean > 0 ? loadRange / loadMean : Double.POSITIVE_INFINITY;

        // 方差的最大值和最小值之间的差异
        double varianceRange = Arrays.stream(diskReadVariances).max().getAsDouble() -
                Arrays.stream(diskReadVariances).min().getAsDouble();

        // 总分 (越小越好) - 增加了最大负载差异和方差差异的惩罚
        return loadCv * 0.4 +
                readVarianceMean * 0.2 +
                loadRangeNormalized * 0.4 +
                varianceRange * 0.0;
    }
}