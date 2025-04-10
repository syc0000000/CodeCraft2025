package Info;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import IO.model.GCCommandOut;
import IO.model.PreprocessOut;
import Info.model.LocalDisk;
import Info.model.ReadTask;
import Info.model.Replica;
import Info.model.Tag;
import Info.model.TagMeta;
import Info.model.UnitData;
import Info.model.UserObject;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

// Info模块 - 管理全局信息和数据结构
public class Info {
    private static final ModuleLogger log = LoggerFactory.getLogger("Info");
    /** 硬盘数量 */
    public static int diskNum;
    /** 存储单元数量 */
    public static int unitNum;
    /** 垃圾回收操作次数 */
    public static int gcNum;

    /** 已经存储的对象数量 */
    public static int objNums;
    /** 当前时间戳 */
    public static int timestamp;
    /** 总tick数 */
    public static int tickNums;
    /** 每tick令牌数 */
    public static int tokenPerTick;
    /** 标签数量 */
    public static int tagNums;

    /** 最大读写空间 */
    @Deprecated
    public static int MAX_RW_END;

    /** 对象id和对象的映射 */
    public static HashMap<Integer, UserObject> objMap = new HashMap<>();
    /** 本地磁盘信息 */
    public static ArrayList<LocalDisk> localDiskTbl = new ArrayList<>();
    /** 标签信息 */
    public static ArrayList<Tag> tags = new ArrayList<>();

    /** 存放当前[tick-105, tick]时间片内未完成的读取任务的id，对于tick-105到达的任务, 最晚要在tick上报 */
    public static LinkedList<HashSet<ReadTask>> readTasksInRecent105Tick = new LinkedList<>();
    /** 各时间段累计差值数据 */
    @Deprecated
    public static ArrayList<ArrayList<Integer>> cumulative_write_minus_del = new ArrayList<>();
    /** 每个period要读取的Tag Id，period范围[0, periodNum-1] */
    public static ArrayList<HashSet<Integer>> periodToTagSet = new ArrayList<>();

    // 私有变量
    /** 每个period读取的tag的size 一级是period，二级是tag */
    public static ArrayList<ArrayList<Integer>> readSizeByPeriod = new ArrayList<>();
    /** 每个period读取的tag的密度 一级是period，二级是tag */
    private static ArrayList<ArrayList<Double>> tagDensities = new ArrayList<>();
    /** 每个period读取的tag的size占比 一级是period，二级是tag */
    private static ArrayList<ArrayList<Double>> tagSizeRatio = new ArrayList<>();

    // 根据预处理结果初始化系统参数
    public static void initFromPreprocessOut(PreprocessOut preOut,
            ArrayList<ArrayList<Integer>> fre_read, ArrayList<ArrayList<Integer>> fre_write,
            ArrayList<ArrayList<Integer>> fre_del) {
        tickNums = preOut.T; // 总tick数
        tagNums = preOut.M; // 标签总数
        diskNum = preOut.N; // 硬盘个数
        unitNum = preOut.V; // 每个硬盘存储单元数
        tokenPerTick = preOut.G; // 每tick Token数
        gcNum = preOut.K; // 垃圾回收操作次数
        MAX_RW_END = (int) (unitNum / 2.91) - 1; // 最大读写空间
        // 初始化磁盘表
        for (int i = 0; i < diskNum; i++) {
            localDiskTbl.add(LocalDisk.createDisk(i, unitNum, "tag"));
        }
        // 清空映射
        objMap.clear();
        tags.clear();
        // 初始化tag
        initTag(fre_read, fre_write, fre_del);
        initReadMetricsForPeriods(fre_read);
        // 选择8个标签，在这里调参
        // selectTagsByRatio(8);
        // selectTagsByDensity(9);
        // 16个小于1的数的方差范围是0-0.25
        selectTagsByDynamicVariance1(0.2, 9, 16);
    }

    /**
     * 初始化tag
     */
    private static void initTag(ArrayList<ArrayList<Integer>> fre_read,
            ArrayList<ArrayList<Integer>> fre_write, ArrayList<ArrayList<Integer>> fre_del) {
        // 基础初始化
        for (int i = 0; i < tagNums; i++) {
            Tag tag = new Tag(i, 0, diskNum);
            tags.add(tag);
            // 利用write和del初始化cumulative_write_minus_del
            int now_size = 0;
            int periodCount = (Info.tickNums - 1) / 1800 + 1;
            for (int j = 0; j < periodCount; j++) {
                tag.totalSizeByPeriod.add(now_size);
                now_size += fre_write.get(i).get(j);
                now_size -= fre_del.get(i).get(j);
                tag.readSizeByPeriod.add(fre_read.get(i).get(j));
            }
            tag.totalSizeByPeriod.add(now_size);
            // 扫sizeByPeriod, 找到最大的size
            int max_size = 0;
            for (int j = 0; j < tag.totalSizeByPeriod.size(); j++) {
                if (tag.totalSizeByPeriod.get(j) > max_size) {
                    max_size = tag.totalSizeByPeriod.get(j);
                }
            }
            tag.sizeMax = max_size;
        }
    }

    private static void initReadMetricsForPeriods(ArrayList<ArrayList<Integer>> fre_read) {
        // 初始化readSizeByPeriod
        int periodCount = (tickNums - 1) / 1800 + 1;

        // 清空并初始化periodToTagSet
        periodToTagSet.clear();
        for (int periodIdx = 0; periodIdx < periodCount; periodIdx++) {
            // 初始化periodToTagSet
            periodToTagSet.add(new HashSet<>());
            // 初始化每个period下各个Tag的读取大小
            readSizeByPeriod.add(new ArrayList<>());
            for (int tagIdx = 0; tagIdx < tagNums; tagIdx++) {
                readSizeByPeriod.get(periodIdx).add(fre_read.get(tagIdx).get(periodIdx));
            }

            // 初始化每个period下各个Tag的密度(readSize / tagLength)
            tagDensities.add(new ArrayList<>());
            for (int tagIdx = 0; tagIdx < tagNums; tagIdx++) {
                double density = (double) readSizeByPeriod.get(periodIdx).get(tagIdx)
                        / tags.get(tagIdx).sizeMax;
                tagDensities.get(periodIdx).add(density);
            }

            // 初始化每个period下各个Tag的size占比
            tagSizeRatio.add(new ArrayList<>());
            for (int tagIdx = 0; tagIdx < tagNums; tagIdx++) {
                // 该period下tag的size占比
                // ratio = readSizeOfTag1 / readSizeOfTag1 + readSizeOfTag2 + ... +
                // readSizeOfTagN
                double ratio = (double) readSizeByPeriod.get(periodIdx).get(tagIdx) / readSizeByPeriod
                        .get(periodIdx).stream().mapToInt(Integer::intValue).sum();
                tagSizeRatio.get(periodIdx).add(ratio);
            }
        }
    }

    /**
     * 为每个周期选择标签，选择的标签数量为pickCount
     * 
     * @param pickCount
     */
    private static void selectTagsByDensity(int pickCount) {
        for (int periodIdx = 0; periodIdx < readSizeByPeriod.size(); periodIdx++) {
            ArrayList<Double> densityData = tagDensities.get(periodIdx);
            // 创建密度数据的副本，避免修改原始数据
            ArrayList<Double> tempDensities = new ArrayList<>(densityData);

            // 选择标签
            for (int i = 0; i < pickCount; i++) {
                int maxIndex = -1;
                double maxDensity = -1;
                for (int j = 0; j < tempDensities.size(); j++) {
                    if (tempDensities.get(j) > maxDensity) {
                        maxDensity = tempDensities.get(j);
                        maxIndex = j;
                    }
                }
                if (maxIndex != -1) {
                    periodToTagSet.get(periodIdx).add(maxIndex);
                    tempDensities.set(maxIndex, -1.0); // 标记为已选择（在副本上操作）
                }
            }
        }
    }

    private static void selectTagsByRatio(int pickCount) {
        for (int periodIdx = 0; periodIdx < readSizeByPeriod.size(); periodIdx++) {
            ArrayList<Double> ratioData = tagSizeRatio.get(periodIdx);
            // 创建密度数据的副本，避免修改原始数据
            ArrayList<Double> tempRatios = new ArrayList<>(ratioData);

            // 选择标签
            for (int i = 0; i < pickCount; i++) {
                int maxIndex = -1;
                double maxDensity = -1;
                for (int j = 0; j < tempRatios.size(); j++) {
                    if (tempRatios.get(j) > maxDensity) {
                        maxDensity = tempRatios.get(j);
                        maxIndex = j;
                    }
                }
                if (maxIndex != -1) {
                    periodToTagSet.get(periodIdx).add(maxIndex);
                    tempRatios.set(maxIndex, -1.0); // 标记为已选择（在副本上操作）
                }
            }
        }
    }

    /**
     * 根据当前period，不同tag的read方差，方差越小、选择的tag越多 然后根据tag密度选择前n个标签
     * 
     * @param varianceThreshold 方差阈值
     * @param minCount          每个周期至少选择的标签数量
     * @param maxCount          每个周期最多选择的标签数量
     */
    private static void selectTagsByDynamicVariance1(double varianceThreshold, int minCount,
            int maxCount) {
        for (int periodIdx = 0; periodIdx < readSizeByPeriod.size(); periodIdx++) {
            ArrayList<Double> ratioData = tagSizeRatio.get(periodIdx);
            ArrayList<Double> tempRatios = new ArrayList<>(ratioData);
            ArrayList<Integer> selectedTags = new ArrayList<>();

            // 先选择比例最高的标签作为起点
            int firstTag = getMaxRatioTagIndex(tempRatios);
            selectedTags.add(firstTag);
            tempRatios.set(firstTag, -1.0);

            // 确保至少选择minTags个标签
            while (selectedTags.size() < minCount) {
                int nextTag = getMinVarianceTag(tempRatios, selectedTags, ratioData);
                if (nextTag == -1)
                    break; // 没有更多标签可选

                selectedTags.add(nextTag);
                tempRatios.set(nextTag, -1.0);
            }

            // 动态添加更多标签，直到方差低于阈值或达到最大标签数
            double currentVariance = calculateVariance(selectedTags, ratioData);
            while (selectedTags.size() < maxCount) {
                int nextTag = getMinVarianceTag(tempRatios, selectedTags, ratioData);
                if (nextTag == -1)
                    break; // 没有更多标签可选

                // 临时添加标签并计算新方差
                selectedTags.add(nextTag);
                double newVariance = calculateVariance(selectedTags, ratioData);

                // 如果新方差更低或已达到最小标签数但方差仍可接受，保留这个标签
                if (newVariance < currentVariance
                        || (selectedTags.size() <= minCount && newVariance < varianceThreshold)) {
                    tempRatios.set(nextTag, -1.0);
                    currentVariance = newVariance;
                } else {
                    // 否则移除这个标签
                    selectedTags.remove(selectedTags.size() - 1);
                    log.debug("Period " + periodIdx + " Variance: " + currentVariance
                            + ", Selected " + selectedTags.size() + " tags");
                    break; // 如果添加更多标签不会减少方差，则停止
                }
            }

            // 确定要选择的标签数量
            int tagsToSelect = selectedTags.size();

            // 加载配置策略：1. 命令行参数 2. 配置文件 3. 默认值
            HashMap<Integer, Integer> peroid2Count = loadPeriodTagConfig();

            // 使用配置的标签数量
            if (peroid2Count.get(periodIdx) != null) {
                tagsToSelect = peroid2Count.get(periodIdx);
            }

            // 根据密度选择前n个标签
            ArrayList<Double> densityData = tagDensities.get(periodIdx);
            ArrayList<Integer> tagIndices = new ArrayList<>();
            ArrayList<Double> tempDensities = new ArrayList<>(densityData);

            // 创建临时的索引-密度对
            for (int i = 0; i < tempDensities.size(); i++) {
                tagIndices.add(i);
            }

            // 根据密度降序排序标签索引
            tagIndices.sort((a, b) -> Double.compare(tempDensities.get(b), tempDensities.get(a)));

            // 选择前n个高密度标签
            for (int i = 0; i < Math.min(tagsToSelect, tagIndices.size()); i++) {
                periodToTagSet.get(periodIdx).add(tagIndices.get(i));
            }

            log.debug("Period " + periodIdx + ": Selected " + periodToTagSet.get(periodIdx).size()
                    + " tags based on density after variance calculation");
        }
    }

    /**
     * 加载标签配置，优先级: 命令行参数 > 配置文件 > 默认值
     * 
     * @return 期间到标签数量的映射
     */
    private static HashMap<Integer, Integer> loadPeriodTagConfig() {
        HashMap<Integer, Integer> peroid2Count = new HashMap<>();

        // 1. 尝试从命令行参数读取
        try {
            String periodParam = System.getProperty("period");
            String tagsParam = System.getProperty("tags");

            if (periodParam != null && tagsParam != null) {
                int period = Integer.parseInt(periodParam);
                int tags = Integer.parseInt(tagsParam);
                peroid2Count.put(period, tags);
                log.info("从命令行读取参数 - Period " + period + ": " + tags + " 标签");
                // 命令行参数只适用于单个period，直接返回
                return peroid2Count;
            }
        } catch (Exception e) {
            log.warn("解析命令行参数失败: " + e.getMessage());
        }

        // 2. 从文件中读取最优参数
        try {
            java.nio.file.Path optimalParamsPath = java.nio.file.Paths.get("optimal_period_tags.txt");
            if (java.nio.file.Files.exists(optimalParamsPath)) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(optimalParamsPath);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue; // 跳过空行和注释
                    }
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        int period = Integer.parseInt(parts[0].trim());
                        int count = Integer.parseInt(parts[1].trim());
                        peroid2Count.put(period, count);
                        log.debug("从文件加载参数 - Period " + period + ": " + count + " 标签");
                    }
                }
            } else {
                log.debug("参数文件不存在，使用默认配置");
            }
        } catch (Exception e) {
            log.error("读取最优参数文件失败: " + e.getMessage());
        }

        // 3. 如果没有从文件读取到参数，使用硬编码的默认值
        if (peroid2Count.isEmpty()) {
            peroid2Count.put(0, 16);
            peroid2Count.put(1, 16);
            peroid2Count.put(2, 16);
            peroid2Count.put(3, 16);
            peroid2Count.put(4, 14);
            peroid2Count.put(5, 12);
            peroid2Count.put(6, 6);
            peroid2Count.put(7, 5);
            peroid2Count.put(8, 14);
            peroid2Count.put(9, 9);
            peroid2Count.put(10, 6);
            peroid2Count.put(11, 7);
            peroid2Count.put(12, 8);
            peroid2Count.put(13, 10);
            peroid2Count.put(14, 9);
            peroid2Count.put(15, 8);
            peroid2Count.put(16, 5);
            peroid2Count.put(17, 7);
            peroid2Count.put(18, 5);
            peroid2Count.put(19, 7);
            peroid2Count.put(20, 5);
            peroid2Count.put(21, 7);
            peroid2Count.put(22, 5);
        }

        return peroid2Count;
    }

    /**
     * 获取大小比例最高的标签索引
     */
    private static int getMaxRatioTagIndex(ArrayList<Double> ratios) {
        int maxIndex = 0;
        double maxRatio = ratios.get(0);
        for (int i = 1; i < ratios.size(); i++) {
            if (ratios.get(i) > maxRatio) {
                maxRatio = ratios.get(i);
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /**
     * 计算选中标签的大小比例方差
     */
    private static double calculateVariance(ArrayList<Integer> tagIds, ArrayList<Double> ratios) {
        double sum = 0;
        for (int tagId : tagIds) {
            sum += ratios.get(tagId);
        }
        double mean = sum / tagIds.size();

        double squareSum = 0;
        for (int tagId : tagIds) {
            double diff = ratios.get(tagId) - mean;
            squareSum += diff * diff;
        }
        double variance = squareSum / tagIds.size();
        return variance;
    }

    /**
     * 获取导致最小方差的标签
     */
    private static int getMinVarianceTag(ArrayList<Double> ratioCandidates,
            ArrayList<Integer> selectedTags, ArrayList<Double> ratiosForVariance) {
        int bestTag = -1;
        double minVariance = Double.MAX_VALUE;

        for (int tagIdx = 0; tagIdx < ratioCandidates.size(); tagIdx++) {
            if (ratioCandidates.get(tagIdx) < 0) // 略过已选择的标签
                continue;

            selectedTags.add(tagIdx);
            double variance = calculateVariance(selectedTags, ratiosForVariance);
            selectedTags.remove(selectedTags.size() - 1);

            if (variance < minVariance) {
                minVariance = variance;
                bestTag = tagIdx;
            }
        }

        return bestTag;
    }

    /**
     * 打印tagDensities, periodToTagSet
     * 
     * @return
     */
    public static String tagInfoString() {
        StringBuilder sb = new StringBuilder();
        sb.append("periodToTagSet:\n");
        for (int period = 0; period < periodToTagSet.size(); period++) {
            sb.append("Period ").append(period + 1).append(": ");
            HashSet<Integer> tags = periodToTagSet.get(period);
            for (Integer tag : tags) {
                sb.append(tag).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Export readSizeByPeriod data to CSV format
     * 
     * @param filepath The path to save the CSV file
     * @return true if export successful, false otherwise
     */
    public static void exportTagInfoToCSV() {
        StringBuilder readSizeCSV = new StringBuilder();
        StringBuilder tagDensityCSV = new StringBuilder();
        StringBuilder tagSizeRatioCSV = new StringBuilder();

        // Add header row with tag numbers
        readSizeCSV.append("Period|Tag");
        tagDensityCSV.append("Period|Tag");
        tagSizeRatioCSV.append("Period|Tag");

        for (int i = 1; i <= tagNums; i++) {
            readSizeCSV.append(",").append(i);
            tagDensityCSV.append(",").append(i);
            tagSizeRatioCSV.append(",").append(i);
        }
        readSizeCSV.append("\n");
        tagDensityCSV.append("\n");
        tagSizeRatioCSV.append("\n");

        // Add data rows
        for (int period = 0; period < readSizeByPeriod.size(); period++) {
            ArrayList<Integer> periodData = readSizeByPeriod.get(period);
            ArrayList<Double> periodDensity = tagDensities.get(period);
            ArrayList<Double> periodSizeRatio = tagSizeRatio.get(period);

            readSizeCSV.append(period + 1); // Period numbers start from 1
            tagDensityCSV.append(period + 1); // Period numbers start from 1
            tagSizeRatioCSV.append(period + 1); // Period numbers start from 1
            for (int tagValue : periodData) {
                readSizeCSV.append(",").append(tagValue);
            }
            for (double densityValue : periodDensity) {
                tagDensityCSV.append(",").append(densityValue);
            }
            for (double ratioValue : periodSizeRatio) {
                tagSizeRatioCSV.append("%,").append(ratioValue);
            }
            readSizeCSV.append("\n");
            tagDensityCSV.append("\n");
            tagSizeRatioCSV.append("%\n");
        }

        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("logs/readSizeByPeriod.csv"),
                    readSizeCSV.toString().getBytes());
            java.nio.file.Files.write(java.nio.file.Paths.get("logs/tagDensityByPeriod.csv"),
                    tagDensityCSV.toString().getBytes());
            java.nio.file.Files.write(java.nio.file.Paths.get("logs/tagSizeRatioByPeriod.csv"),
                    tagSizeRatioCSV.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<GCCommandOut> GC() {
        // 临时返回空列表，等待实现完整的GC功能
        return new ArrayList<>();
    }
}