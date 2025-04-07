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
    /** 每个period读取的tag的size 一级是period，二级是tag */
    public static ArrayList<ArrayList<Integer>> readSizeByPeriod = new ArrayList<>();
    /** 每个period读取的tag的密度 一级是period，二级是tag */
    public static ArrayList<ArrayList<Double>> tagDensities = new ArrayList<>();

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
        initReadSizeByPeriod(fre_read);
        // 选择8个标签，在这里调参
        selectTagsByCount(8);
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

    private static void initReadSizeByPeriod(ArrayList<ArrayList<Integer>> fre_read) {
        // 初始化readSizeByPeriod
        int periodCount = (tickNums - 1) / 1800 + 1;
        
        for (int periodIdx = 0; periodIdx < periodCount; periodIdx++) {
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
        }
    }

    /**
     * 为每个周期选择标签，选择的标签数量为pickCount
     * 
     * @param pickCount
     */
    private static void selectTagsByCount(int pickCount) {
        for (int periodIdx = 0; periodIdx < readSizeByPeriod.size(); periodIdx++) {
            ArrayList<Double> densityData = tagDensities.get(periodIdx);

            // 选择标签
            for (int i = 0; i < pickCount; i++) {
                int maxIndex = -1;
                double maxDensity = -1;
                for (int j = 0; j < densityData.size(); j++) {
                    if (densityData.get(j) > maxDensity) {
                        maxDensity = densityData.get(j);
                        maxIndex = j;
                    }
                }
                if (maxIndex != -1) {
                    periodToTagSet.get(periodIdx).add(maxIndex);
                    densityData.set(maxIndex, -1.0); // 标记为已选择
                }
            }
        }
    }

    /**
     * Export readSizeByPeriod data to CSV format
     * 
     * @param filepath The path to save the CSV file
     * @return true if export successful, false otherwise
     */
    public static void exportReadSizeByPeriodToCSV(String filepath) {
        StringBuilder csv = new StringBuilder();

        // Add header row with tag numbers
        csv.append("Period|Tag");
        for (int i = 1; i <= tagNums; i++) {
            csv.append(",").append(i);
        }
        csv.append("\n");

        // Add data rows
        for (int period = 0; period < readSizeByPeriod.size(); period++) {
            ArrayList<Integer> periodData = readSizeByPeriod.get(period);

            csv.append(period + 1); // Period numbers start from 1
            for (int tagValue : periodData) {
                csv.append(",").append(tagValue);
            }
            csv.append("\n");
        }

        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(filepath), csv.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
