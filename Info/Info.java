package Info;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import IO.model.PreprocessOut;
import Info.model.LocalDisk;
import Info.model.ReadTask;
import Info.model.Tag;
import Info.model.UserObject;

// Info模块 - 管理全局信息和数据结构
public class Info {
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
    private static ArrayList<ArrayList<Integer>> readSizeByPeriod = new ArrayList<>();
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
        selectTagsByDensity(8);
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
                // ratio = readSizeOfTag1 / readSizeOfTag1 + readSizeOfTag2 + ... + readSizeOfTagN
                double ratio =
                        (double) readSizeByPeriod.get(periodIdx).get(tagIdx) / readSizeByPeriod
                                .get(periodIdx).stream().mapToInt(Integer::intValue).sum() * 100;
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
}
