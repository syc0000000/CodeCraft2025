package Info;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import IO.model.PreprocessOut;
import Info.model.*;

// Info模块 - 管理全局信息和数据结构
public class Info {
    /** 硬盘数量 */
    public static int diskNum;
    /** 存储单元数量 */
    public static int unitNum;

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
    public static int MAX_RW_END;

    /** 对象id和对象的映射 */
    public static HashMap<Integer, UserObject> objMap = new HashMap<>();
    /** 本地磁盘信息 */
    public static ArrayList<LocalDisk> localDiskTbl = new ArrayList<>();
    /** 标签信息 */
    public static ArrayList<Tag> tags = new ArrayList<>();

    /** 各时间段累计差值数据 */
    public static ArrayList<ArrayList<Integer>> cumulative_write_minus_del = new ArrayList<>();
    /** 每个period要读取的Tag Id，period范围[0, periodNum-1] */
    public static ArrayList<HashSet<Integer>> periodToTagSet = new ArrayList<>();
    /** 每个period读取的tag的size 一级是tag，二级是period */
    public static ArrayList<ArrayList<Integer>> fre_read = new ArrayList<>();
    /** 每个period读取的tag的size 一级是period，二级是tag */
    public static ArrayList<ArrayList<Integer>> readSizeByPeriod = new ArrayList<>();

    // 初始化Info模块
    public static void init() {
        // 重置计数器
        diskNum = 0;
        unitNum = 0;

        // 重置全局参数
        objNums = 0;
        timestamp = 0;

        // 清空映射
        objMap.clear();
        localDiskTbl.clear();
    }

    // 根据预处理结果初始化系统参数
    public static void initFromPreprocessOut(PreprocessOut preOut) {
        tickNums = preOut.T; // 总tick数
        tagNums = preOut.M; // 标签总数
        diskNum = preOut.N; // 硬盘个数
        unitNum = preOut.V; // 每个硬盘存储单元数
        tokenPerTick = preOut.G; // 每tick Token数
        MAX_RW_END = (int) (unitNum / 2.9); // 最大读写空间
        // 初始化磁盘表
        for (int i = 0; i < diskNum; i++) {
            localDiskTbl.add(LocalDisk.createDisk(i, unitNum, "tag"));
        }
        // 清空映射
        objMap.clear();
    }

}
