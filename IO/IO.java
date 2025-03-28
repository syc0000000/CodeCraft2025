package IO;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import IO.model.CompleteCommandOut;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import IO.model.PreprocessOut;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.Info;
import Info.Info.Action;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * IO模块的主要接口 只负责输入输出的解析和格式化，不负责业务逻辑处理
 */
public class IO {
    private static final double TAG_THRESHOLD = 0.95; // 每个period，readSize从高到低，选择95%的标签
    private static final int FRE_PER_SLICING = 1800;
    private static Scanner scanner = new Scanner(System.in);
    private static final ModuleLogger log = LoggerFactory.getLogger("IO");
    public static ArrayList<Integer> tagsUnitUsage = new ArrayList<>(); // 硬盘使用率数据
    public static ArrayList<ArrayList<Integer>> cumulative_write_minus_del = new ArrayList<>(); // 各时间段累计差值数据
    /** 每个period要读取的Tag Id，period范围[0, periodNum-1] */
    public static ArrayList<HashSet<Integer>> periodToTagSet = new ArrayList<>();
    /** 每个period读取的tag的size 一级是tag，二级是period */
    public static ArrayList<ArrayList<Integer>> fre_read = new ArrayList<>();

    /**
     * 预处理
     * 
     * @return 预处理输出结构
     */
    public static PreprocessOut preprocess() {
        int T = 0; // 总tick数
        int M = 0; // 标签总数
        int N = 0; // 硬盘个数
        int V = 0; // 每个硬盘存储单元数
        int G = 0; // 每tick Token数

        T = scanner.nextInt();
        M = scanner.nextInt();
        N = scanner.nextInt();
        V = scanner.nextInt();
        G = scanner.nextInt();

        // 保存删除频率数据
        ArrayList<ArrayList<Integer>> fre_del = new ArrayList<>();
        for (int i = 0; i < M; i++) {
            ArrayList<Integer> tagData = new ArrayList<>();
            for (int j = 0; j < (T - 1) / FRE_PER_SLICING + 1; j++) {
                tagData.add(scanner.nextInt());
            }
            fre_del.add(tagData);
        }

        // 保存写入频率数据
        ArrayList<ArrayList<Integer>> fre_write = new ArrayList<>();
        for (int i = 0; i < M; i++) {
            ArrayList<Integer> tagData = new ArrayList<>();
            for (int j = 0; j < (T - 1) / FRE_PER_SLICING + 1; j++) {
                tagData.add(scanner.nextInt());
            }
            fre_write.add(tagData);
        }

        // 读取读取频率数据
        ArrayList<ArrayList<Integer>> fre_read = new ArrayList<>();
        for (int i = 0; i < M; i++) {
            ArrayList<Integer> tagData = new ArrayList<>();
            for (int j = 0; j < (T - 1) / FRE_PER_SLICING + 1; j++) {
                tagData.add(scanner.nextInt());
            }
            fre_read.add(tagData);
        }

        // 处理读取频率，转换一下，一级key为period，二级key为tag
        int periodCount = (T - 1) / FRE_PER_SLICING + 1;
        ArrayList<ArrayList<Integer>> readSizeByPeriod = new ArrayList<>();
        for (int j = 0; j < periodCount; j++) {
            readSizeByPeriod.add(new ArrayList<>());
        }
        for (int j = 0; j < periodCount; j++) {
            for (int i = 0; i < M; i++) {
                // 从fre_read中获取tag i在period j的数据
                readSizeByPeriod.get(j).add(fre_read.get(i).get(j));
            }
        }
        IO.fre_read = fre_read; // 保存fre_read
        IO.periodToTagSet = pickTagsForPeriods(readSizeByPeriod);
        // for (int i = 0; i < periodToTagSet.size(); i++) {
        // log.info("Period " + i + " 选择的Tag: " + periodToTagSet.get(i));
        // }

        // 计算写入-删除的累计差值
        for (int i = 0; i < M; i++) {
            ArrayList<Integer> diffData = new ArrayList<>();
            for (int j = 0; j < fre_write.get(i).size(); j++) {
                diffData.add(fre_write.get(i).get(j) - fre_del.get(i).get(j));
            }
            cumulative_write_minus_del.add(calculateCumulative(diffData));
            // tagsUnitUsage中添加cumulative_write_minus_del[i]中的最大的数据
            int max = 0;
            for (int j = 0; j < cumulative_write_minus_del.get(i).size(); j++) {
                if (cumulative_write_minus_del.get(i).get(j) > max) {
                    max = cumulative_write_minus_del.get(i).get(j);
                }
            }
            IO.tagsUnitUsage.add(max);
        }
        for (int i = 0; i < cumulative_write_minus_del.size(); i++) {
            log.info(cumulative_write_minus_del.get(i).toString());
        }

        System.out.println("OK");
        flushAll();

        PreprocessOut out = new PreprocessOut();
        out.T = T;
        out.M = M;
        out.N = N;
        out.V = V;
        out.G = G;
        return out;
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

    /**
     * 处理时间戳（特殊情况，直接处理）
     */
    public static void processTimeStamp() {
        String cmd = scanner.next(); // 读取命令名称 "TIMESTAMP"
        int timeStamp = scanner.nextInt();

        // 将当前帧写入Info模块
        Info.timestamp = timeStamp;

        System.out.println("TIMESTAMP " + timeStamp);
        flushAll();
    }

    /**
     * 从标准输入读取写命令
     * 
     * @return 写命令输入结构列表
     */
    public static ArrayList<WriteCommandIn> readWriteCommand() {
        ArrayList<WriteCommandIn> in = new ArrayList<>();
        int size = scanner.nextInt();

        for (int i = 0; i < size; i++) {
            int objId = scanner.nextInt();
            int size_ = scanner.nextInt();
            int tag = scanner.nextInt();
            in.add(new WriteCommandIn(objId, size_, tag));
        }

        return in;
    }

    /**
     * 输出写命令结果到标准输出
     * 
     * @param out 写命令输出结构列表
     */
    public static void writeWriteCommand(List<WriteCommandOut> out) {
        if (out == null) {
            return;
        }

        int size = out.size();
        log.info("输出写命令结果: " + out.size() + " 条");
        for (int i = 0; i < size; i++) {
            System.out.print(out.get(i)); // 这里会自动调用toString进行类型转型，log没支持这个feat
            log.info("如下为输出的命令结果: \n" + out.get(i).toString());
        }

        flushAll();
    }

    /**
     * 从标准输入读取删除命令
     * 
     * @return 删除命令输入结构
     */
    public static ArrayList<DeleteCommandIn> readDeleteCommand() {
        ArrayList<DeleteCommandIn> in = new ArrayList<>();
        int size = scanner.nextInt();
        for (int i = 0; i < size; i++) {
            int objId = scanner.nextInt();
            in.add(new DeleteCommandIn(objId));
        }
        return in;
    }

    /**
     * 输出删除命令结果到标准输出
     * 
     * @param out 删除命令输出结构
     */
    public static void writeDeleteCommand(ArrayList<DeleteCommandOut> out) {
        int size = out.size();
        System.out.println(size);
        for (int i = 0; i < size; i++) {
            System.out.println(out.get(i).readCommandId);
        }
        flushAll();
    }

    /**
     * 从标准输入读取读命令
     * 
     * @return 读命令输入结构列表
     */
    public static ArrayList<ReadCommandIn> readReadCommand() {
        ArrayList<ReadCommandIn> in = new ArrayList<>();
        int size = scanner.nextInt();

        for (int i = 0; i < size; i++) {
            int commandId = scanner.nextInt();
            int objId = scanner.nextInt();
            in.add(new ReadCommandIn(commandId, objId));
        }

        return in;
    }

    /**
     * 输出读命令结果到标准输出
     * 
     * @param out 读命令输出结构，key为磁盘ID，value为读命令输出结构
     * @note 只需要放动了的命令，如果磁头完全不动，则不需要传入
     */
    public static void writeReadCommand(Map<Integer, ReadCommandOut> out) {
        for (int i = 0; i < Info.diskNum; i++) {
            if (out.containsKey(i)) {
                // 判断是否是JUMP操作
                if (out.get(i).actions != null && !out.get(i).actions.isEmpty()
                        && out.get(i).actions.get(0) == Action.JUMP) {
                    int target = out.get(i).jumpTarget + 1;
                    System.out.println("j " + target);
                } else {
                    // 输出读取或通过操作
                    for (Action action : out.get(i).actions) {
                        if (action == Action.READ) {
                            System.out.print("r");
                        } else if (action == Action.PASS) {
                            System.out.print("p");
                        }
                    }
                    System.out.println("#");
                }
            } else {
                System.out.println("#");
            }
        }
        // 注意：此处不刷新输出
    }

    /**
     * 输出读取完成命令结果到标准输出
     * 
     * @param out 读取完成命令输出结构列表
     */
    public static void writeCompleteCommand(HashSet<CompleteCommandOut> out) {
        int size = out.size();
        System.out.println(size);

        for (CompleteCommandOut completeCommandOut : out) {
            System.out.println(completeCommandOut.commandId);
        }

        flushAll();
    }

    /**
     * 输出全部命令
     */
    public static void flushAll() {
        System.out.flush();
    }
}
