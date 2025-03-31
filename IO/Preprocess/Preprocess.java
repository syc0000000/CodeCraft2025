package IO.Preprocess;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;
import IO.IO;
import IO.model.PreprocessOut;
import Info.Info;
import IO.Preprocess.TagDistribution.TagDistributionManager;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class Preprocess {
    private static final ModuleLogger log = LoggerFactory.getLogger("Preprocess");
    private static final Scanner scanner = new Scanner(System.in);
    private static final int FRE_PER_SLICING = 1800;
    private static ArrayList<ArrayList<Integer>> cumulative_write_minus_del = new ArrayList<>();
    private static ArrayList<Integer> tagsUnitUsage = new ArrayList<>();

    // 基本参数
    private static int T; // 总tick数
    private static int M; // 标签总数
    private static int N; // 硬盘个数
    private static int V; // 每个硬盘存储单元数
    private static int G; // 每tick Token数

    // 标签分布处理参数
    private static String loadDistributionPath = null;
    private static String loadTagsPath = null;

    // getter方法
    public static ArrayList<ArrayList<Integer>> getCumulativeWriteMinusDel() {
        return cumulative_write_minus_del;
    }

    public static ArrayList<Integer> getTagsUnitUsage() {
        return tagsUnitUsage;
    }

    /**
     * 预处理主函数
     */
    public static void preprocess() {
        // 读取基本参数
        readBasicParameters();

        // 读取频率数据
        ArrayList<ArrayList<Integer>> fre_del = readFrequencyData();
        ArrayList<ArrayList<Integer>> fre_write = readFrequencyData();
        ArrayList<ArrayList<Integer>> fre_read = readFrequencyData();

        // 处理读取频率数据
        processReadFrequency(fre_read);

        // 处理写入和删除频率数据
        processWriteAndDeleteFrequency(fre_write, fre_del);

        // 初始化系统信息
        initializeSystemInfo();

        // 处理标签分布和排序
        TagDistributionManager.initializeTagDistribution(loadDistributionPath, loadTagsPath);

        System.out.println("OK");
        flushAll();
    }

    /**
     * 设置分布和排序加载路径
     */
    public static void setDistributionPaths(String distributionPath, String tagsPath) {
        loadDistributionPath = distributionPath;
        loadTagsPath = tagsPath;
    }

    /**
     * 初始化系统信息
     */
    private static void initializeSystemInfo() {
        // 新建preprocessOut
        PreprocessOut preprocessOut = new PreprocessOut();
        preprocessOut.T = T;
        preprocessOut.M = M;
        preprocessOut.N = N;
        preprocessOut.V = V;
        preprocessOut.G = G;
        Info.initFromPreprocessOut(preprocessOut);
    }

    /**
     * 读取基本参数
     */
    private static void readBasicParameters() {
        T = scanner.nextInt();
        M = scanner.nextInt();
        N = scanner.nextInt();
        V = scanner.nextInt();
        G = scanner.nextInt();
    }

    /**
     * 读取频率数据
     */
    private static ArrayList<ArrayList<Integer>> readFrequencyData() {
        ArrayList<ArrayList<Integer>> frequencyData = new ArrayList<>();
        int periodCount = (T - 1) / FRE_PER_SLICING + 1;

        for (int i = 0; i < M; i++) {
            ArrayList<Integer> tagData = new ArrayList<>();
            for (int j = 0; j < periodCount; j++) {
                tagData.add(scanner.nextInt());
            }
            frequencyData.add(tagData);
        }
        return frequencyData;
    }

    /**
     * 处理读取频率数据
     */
    private static void processReadFrequency(ArrayList<ArrayList<Integer>> fre_read) {
        int periodCount = (T - 1) / FRE_PER_SLICING + 1;

        // 初始化readSizeByPeriod
        for (int j = 0; j < periodCount; j++) {
            Info.readSizeByPeriod.add(new ArrayList<>());
        }

        // 转换数据结构
        for (int j = 0; j < periodCount; j++) {
            for (int i = 0; i < fre_read.size(); i++) {
                Info.readSizeByPeriod.get(j).add(fre_read.get(i).get(j));
            }
        }

        Info.fre_read = fre_read;
        Info.periodToTagSet = pickTagsForPeriods(Info.readSizeByPeriod);
    }

    /**
     * 处理写入和删除频率数据
     */
    private static void processWriteAndDeleteFrequency(
            ArrayList<ArrayList<Integer>> fre_write,
            ArrayList<ArrayList<Integer>> fre_del) {
        for (int i = 0; i < M; i++) {
            ArrayList<Integer> diffData = new ArrayList<>();
            for (int j = 0; j < fre_write.get(i).size(); j++) {
                diffData.add(fre_write.get(i).get(j) - fre_del.get(i).get(j));
            }
            cumulative_write_minus_del.add(calculateCumulative(diffData));

            // 计算最大使用量
            int max = 0;
            for (int j = 0; j < cumulative_write_minus_del.get(i).size(); j++) {
                if (cumulative_write_minus_del.get(i).get(j) > max) {
                    max = cumulative_write_minus_del.get(i).get(j);
                }
            }
            tagsUnitUsage.add(max);
        }

        // 输出日志
        for (int i = 0; i < cumulative_write_minus_del.size(); i++) {
            log.info(cumulative_write_minus_del.get(i).toString());
        }
    }

    /**
     * 为每个周期选择标签
     * 
     * @param readSizeByPeriod 每个时间段的读取大小
     * @return 每个周期选择的标签集合
     */
    private static ArrayList<HashSet<Integer>> pickTagsForPeriods(
            ArrayList<ArrayList<Integer>> readSizeByPeriod) {
        // 创建返回值数据结构
        ArrayList<HashSet<Integer>> periodToTagSet = new ArrayList<>();

        // 遍历每个时间段
        for (int periodIdx = 0; periodIdx < readSizeByPeriod.size(); periodIdx++) {
            ArrayList<Integer> periodData = readSizeByPeriod.get(periodIdx);
            HashSet<Integer> selectedTagsSet = new HashSet<>();

            // 计算该时间段内所有标签的size总和
            int totalSize = 0;
            for (int size : periodData) {
                totalSize += size;
            }

            // 如果总和为0，添加空集合并跳过此时间段
            if (totalSize == 0) {
                periodToTagSet.add(selectedTagsSet);
                log.debug("时间段 " + periodIdx + " 没有数据 (总大小=0)");
                continue;
            }

            // 创建带索引的标签size列表，以便排序后能找到原始标签
            List<Map.Entry<Integer, Integer>> tagWithIndex = new ArrayList<>();
            for (int i = 0; i < periodData.size(); i++) {
                tagWithIndex.add(new AbstractMap.SimpleEntry<>(i, periodData.get(i)));
            }

            // 按size降序排序
            tagWithIndex.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            // 日志输出：打印每个period内各tag百分比，从高到低
            log.debug("时间段 " + periodIdx + " 标签分布情况（从高到低）:");
            StringBuilder tagDistribution = new StringBuilder();
            for (Map.Entry<Integer, Integer> tag : tagWithIndex) {
                if (tag.getValue() > 0) {
                    double percentage = (double) tag.getValue() / totalSize * 100;
                    tagDistribution
                            .append("标签 " + tag.getKey() + ": " + String.format("%.2f", percentage)
                                    + "% (" + tag.getValue() + "/" + totalSize + "), ");
                }
            }
            log.debug(tagDistribution.toString());

            // 选择占比95%的标签
            int cumulativeSize = 0;
            StringBuilder selectedTagsInfo = new StringBuilder("已选择标签: ");

            for (Map.Entry<Integer, Integer> tag : tagWithIndex) {
                int tagId = tag.getKey();
                int tagSize = tag.getValue();

                // 只添加有数据的标签
                if (tagSize > 0) {
                    selectedTagsSet.add(tagId);
                    cumulativeSize += tagSize;

                    double tagPercentage = (double) tagSize / totalSize * 100;
                    double cumulativePercentage = (double) cumulativeSize / totalSize * 100;
                    selectedTagsInfo.append(
                            "标签 " + tagId + " (" + String.format("%.2f", tagPercentage) + "%), ");

                    if (cumulativeSize >= totalSize * IO.TAG_THRESHOLD) {
                        log.debug("已达到阈值" + IO.TAG_THRESHOLD + "，共选择了 " + selectedTagsSet.size() + " 个标签，累计占比: "
                                + String.format("%.2f", cumulativePercentage) + "%");
                        break;
                    }
                }
            }

            log.debug("时间段 " + periodIdx + ": " + selectedTagsInfo.toString() + " (总累计大小: "
                    + cumulativeSize + "/" + totalSize + ", "
                    + String.format("%.2f", (double) cumulativeSize / totalSize * 100) + "%)");

            // 将这个时间段的标签集合添加到结果中
            periodToTagSet.add(selectedTagsSet);
        }

        return periodToTagSet;
    }

    private static ArrayList<Integer> calculateCumulative(ArrayList<Integer> data) {
        ArrayList<Integer> cumulative = new ArrayList<>();
        cumulative.add(0); // 初始值为0
        int sum = 0;
        for (int value : data) {
            sum += value;
            cumulative.add(sum);
        }
        return cumulative;
    }

    /**
     * 刷新所有输出流
     */
    private static void flushAll() {
        System.out.flush();
    }
}
