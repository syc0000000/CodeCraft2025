package IO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import IO.model.CompleteCommandOut;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import IO.model.BusyCommandOut;
import IO.model.MultiReadCommandOut;
import Info.Info;
import Info.model.Action;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * IO模块的主要接口 只负责输入输出的解析和格式化，不负责业务逻辑处理
 */
public class IO {
    public static final double TAG_THRESHOLD = 0.95; // 每个period，readSize从高到低，选择95%的标签
    public static final int FRE_PER_SLICING = 1800;
    private static Scanner scanner = new Scanner(System.in);
    private static final ModuleLogger log = LoggerFactory.getLogger("IO");

    /**
     * 处理时间戳（特殊情况，直接处理）
     */
    public static void processTimeStamp() {
        scanner.next(); // 读取命令名称 "TIMESTAMP"
        int timeStamp = scanner.nextInt();

        // 将当前帧写入Info模块
        Info.timestamp = timeStamp;

        System.out.println("TIMESTAMP " + timeStamp);
        flushAll();
    }

    /**
     * 处理垃圾回收
     */
    public static void processGC() {
        scanner.next(); // 读取命令 "GARBAGE"
        scanner.next(); // 读取命令 "COLLECTION"
        System.out.println("GARBAGE COLLECTION");
        for (int i = 0; i < Info.diskNum; i++) {
            System.out.println("0");
        }
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
            int tag = scanner.nextInt() - 1;
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
        log.debug("读取到 " + size + " 个删除命令");
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
     * 输出多读命令结果到标准输出
     * 
     * @param out 多读命令输出结构列表
     */
    public static void writeMultiReadCommand(Map<Integer, MultiReadCommandOut> out) {

        for (int i = 0; i < Info.diskNum; i++) {
            // 输出磁头0的动作序列和跳转目标
            if (out.containsKey(i)) {
                for (int j = 0; j < 2; j++) {
                    List<Action> actions = out.get(i).actions.get(j);
                    if (actions != null && !actions.isEmpty()
                            && actions.get(0) == Action.JUMP) {
                        int target = out.get(i).jumpTargets.get(j) + 1;
                        System.out.println("j " + target);
                    } else {
                        for (Action action : actions) {
                            if (action == Action.READ) {
                                System.out.print("r");
                            } else if (action == Action.PASS) {
                                System.out.print("p");
                            }
                        }
                        System.out.println("#");
                    }
                }
            } else {
                // 输出两个#
                System.out.println("#");
                System.out.println("#");
            }

            flushAll();
        }
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
     * 输出忙碌命令结果到标准输出
     * 
     * @param out 忙碌命令输出结构列表
     */
    public static void writeBusyCommand(ArrayList<BusyCommandOut> out) {
        int size = out.size();
        System.out.println(size);
        for (BusyCommandOut busyCommandOut : out) {
            System.out.println(busyCommandOut.commandId);
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
