package IO.model;

import java.util.*;
import IO.GA.*;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * 遗传算法磁盘分配接口类
 * 作为Main与IO.GA下的遗传算法实现之间的接口
 */
public class DiskDistributionGA {
    private static final ModuleLogger log = LoggerFactory.getLogger("DiskGA");

    /**
     * 表示标签在磁盘上的一个分片
     */
    public static class Split {
        public int diskIdx; // 磁盘索引
        public double portion; // 分配比例（百分比）

        public Split(int diskIdx, double portion) {
            this.diskIdx = diskIdx;
            this.portion = portion;
        }

        @Override
        public String toString() {
            return String.format("(磁盘%d:%.2f%%)", diskIdx, portion);
        }
    }

    /**
     * 遗传算法入口点
     * 
     * @param tagValues 每个标签的对象数量
     * @return 每个标签在各个磁盘上的分配情况
     */
    public static Map<Integer, List<Split>> entrypoint(int[] tagValues) {
        log.info("初始化遗传算法参数，收到 " + tagValues.length + " 个标签");

        // 检查标签是否完整
        for (int i = 0; i < tagValues.length; i++) {
            log.info("标签 " + i + " 值: " + tagValues[i]);
        }

        // 初始化遗传算法参数
        GeneticParameters.TAGS = tagValues;

        // 转置累积写入-删除数组，从[标签][时间点]转换为[时间点][标签]
        ArrayList<ArrayList<Integer>> originalData = IO.IO.cumulative_write_minus_del;
        ArrayList<ArrayList<Integer>> transposedData = new ArrayList<>();

        if (originalData.size() > 0) {
            int numTimePoints = originalData.get(0).size();
            int numTags = originalData.size();

            log.info("原始数据维度: [标签=" + numTags + "][时间点=" + numTimePoints + "]");

            // 初始化转置后的数组
            for (int t = 0; t < numTimePoints; t++) {
                transposedData.add(new ArrayList<Integer>());
                // 预分配空间
                for (int tag = 0; tag < numTags; tag++) {
                    transposedData.get(t).add(0);
                }
            }

            // 执行转置
            for (int tag = 0; tag < numTags; tag++) {
                for (int t = 0; t < numTimePoints && t < originalData.get(tag).size(); t++) {
                    transposedData.get(t).set(tag, originalData.get(tag).get(t));
                }
            }

            log.info("转置后数据维度: [时间点=" + transposedData.size() + "][标签=" +
                    (transposedData.size() > 0 ? transposedData.get(0).size() : 0) + "]");
        } else {
            log.error("原始时间点数据为空！");
        }

        // 将转置后的数据传给遗传算法
        GeneticParameters.TAGS_PER_TIMEPOINT = transposedData;

        log.info("TAGS长度: " + GeneticParameters.TAGS.length);
        log.info("TAGS_PER_TIMEPOINT长度: " + GeneticParameters.TAGS_PER_TIMEPOINT.size());

        // 确保TAGS_PER_TIMEPOINT的每个子列表长度与TAGS长度一致
        for (int i = 0; i < GeneticParameters.TAGS_PER_TIMEPOINT.size(); i++) {
            if (GeneticParameters.TAGS_PER_TIMEPOINT.get(i).size() != GeneticParameters.TAGS.length) {
                log.error("时间点 " + i + " 的标签数量 " + GeneticParameters.TAGS_PER_TIMEPOINT.get(i).size() +
                        " 与TAGS数组长度 " + GeneticParameters.TAGS.length + " 不匹配!");
            }
        }

        // 运行遗传算法
        log.info("开始运行遗传算法...");
        GeneticAlgorithm ga = new GeneticAlgorithm();
        Individual bestIndividual = ga.run();
        log.info("遗传算法运行完成");

        // 打印结果
        printSolution(bestIndividual);

        // 转换结果格式
        Map<Integer, List<Split>> result = new HashMap<>();
        Map<Integer, List<Individual.TagDiskAllocation>> tagAllocations = bestIndividual.getTagAllocations();

        log.info("遗传算法结果包含 " + tagAllocations.size() + " 个标签（期望有 " + tagValues.length + " 个标签）");

        // 验证所有标签都已分配
        for (int i = 0; i < tagValues.length; i++) {
            if (!tagAllocations.containsKey(i)) {
                log.error("标签 " + i + " 在最终结果中缺失! 这是算法内部错误，不应该发生。");
                throw new RuntimeException("算法结果缺少标签: " + i);
            }
        }

        // 转换结果格式
        for (Map.Entry<Integer, List<Individual.TagDiskAllocation>> entry : tagAllocations.entrySet()) {
            int tagId = entry.getKey();
            List<Individual.TagDiskAllocation> allocations = entry.getValue();
            List<Split> splits = new ArrayList<>();

            for (Individual.TagDiskAllocation allocation : allocations) {
                int diskIdx = allocation.getDiskIdx();
                double proportion = allocation.getProportion() * 100; // 转换为百分比
                splits.add(new Split(diskIdx, proportion));
            }

            result.put(tagId, splits);
        }

        return result;
    }

    /**
     * 简化版打印方法，只输出基本信息和性能指标
     */
    private static void printSolution(Individual individual) {
        // 计算性能指标
        double[] diskLoads = Utils.calculateDiskLoads(individual);
        double[] diskReadVariances = Utils.calculateDiskReadVariance(individual);
        int totalObjects = Arrays.stream(GeneticParameters.TAGS).sum();

        // 输出标签分配
        log.info("最优分配方案:");
        for (int diskIdx = 0; diskIdx < GeneticParameters.NUM_DISKS; diskIdx++) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("磁盘%d: ", diskIdx));
            for (TagAllocation tag : individual.getDisk(diskIdx)) {
                sb.append(String.format("(标签%d:%.2f%%) ", tag.getTagId(), tag.getProportion() * 100));
            }
            log.info(sb.toString());
        }

        // 输出性能指标
        log.info("性能指标:");
        log.info(String.format("对象分布变异系数: %.4f (总=%d, 均值=%.1f, 标准差=%.1f, 范围=%.1f-%.1f)",
                Utils.standardDeviation(diskLoads) / Utils.mean(diskLoads),
                totalObjects,
                Utils.mean(diskLoads),
                Utils.standardDeviation(diskLoads),
                Arrays.stream(diskLoads).min().getAsDouble(),
                Arrays.stream(diskLoads).max().getAsDouble()));
        log.info(String.format("读取方差: %.2f (均值=%.2f, 范围=%.2f-%.2f)",
                Utils.mean(diskReadVariances),
                Utils.mean(diskReadVariances),
                Arrays.stream(diskReadVariances).min().getAsDouble(),
                Arrays.stream(diskReadVariances).max().getAsDouble()));
    }
}
