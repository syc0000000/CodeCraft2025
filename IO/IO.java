package IO;

import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.HashSet;
import IO.model.*;
import Info.Info;
import Info.Info.Action;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * IO模块的主要接口
 * 只负责输入输出的解析和格式化，不负责业务逻辑处理
 */
public class IO {

    private static final int FRE_PER_SLICING = 1800;
    private static Scanner scanner = new Scanner(System.in);
    private static final ModuleLogger log = LoggerFactory.getLogger("IO");

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

        // 读取读取频率数据，暂时用不到
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < (T - 1) / FRE_PER_SLICING + 1; j++) {
                scanner.nextInt();
            }
        }

        // 计算写入-删除的差值
        ArrayList<ArrayList<Integer>> write_minus_del = new ArrayList<>();
        for (int i = 0; i < M; i++) {
            ArrayList<Integer> diffData = new ArrayList<>();
            for (int j = 0; j < fre_write.get(i).size(); j++) {
                diffData.add(fre_write.get(i).get(j) - fre_del.get(i).get(j));
            }
            write_minus_del.add(diffData);
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
                if (out.get(i).actions != null && !out.get(i).actions.isEmpty() &&
                        out.get(i).actions.get(0) == Action.JUMP) {
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