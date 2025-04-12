package IO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;
import IO.model.CompleteCommandOut;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import IO.model.GCCommandOut;
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
    private static BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    private static PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)), true);
    private static StringTokenizer tokenizer = null;
    private static final ModuleLogger log = LoggerFactory.getLogger("IO");

    // 获取下一个输入标记
    private static String nextToken() {
        try {
            while (tokenizer == null || !tokenizer.hasMoreTokens()) {
                tokenizer = new StringTokenizer(reader.readLine());
            }
            return tokenizer.nextToken();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 获取下一个整数
    private static int nextInt() {
        return Integer.parseInt(nextToken());
    }

    /**
     * 处理时间戳（特殊情况，直接处理）
     */
    public static void processTimeStamp() {
        nextToken(); // 读取命令名称 "TIMESTAMP"
        int timeStamp = nextInt();

        // 将当前帧写入Info模块
        Info.timestamp = timeStamp;
        // 更新tickPerToken
        Info.tokenPerTick = Info.g.get(timeStamp / 1800) + Info.G;

        writer.println("TIMESTAMP " + timeStamp);
        writer.flush();
    }

    /**
     * 处理垃圾回收
     */
    public static void processGC() {
        nextToken(); // 读取命令 "GARBAGE"
        nextToken(); // 读取命令 "COLLECTION"
        writer.println("GARBAGE COLLECTION");
        for (int i = 0; i < Info.diskNum; i++) {
            writer.println("0");
        }
        writer.flush();
    }

    public static void writeGCCommand(List<GCCommandOut> out) {
        nextToken(); // 读取命令 "GARBAGE"
        nextToken(); // 读取命令 "COLLECTION"
        int size = out.size();
        writer.println("GARBAGE COLLECTION");
        for (int i = 0; i < size; i++) {
            log.debug("写入垃圾回收命令: " + out.get(i).toString());
            writer.print(out.get(i).toString());
        }
        writer.flush();
    }

    /**
     * 从标准输入读取写命令
     * 
     * @return 写命令输入结构列表
     */
    public static ArrayList<WriteCommandIn> readWriteCommand() {
        ArrayList<WriteCommandIn> in = new ArrayList<>();
        int size = nextInt();

        for (int i = 0; i < size; i++) {
            int objId = nextInt();
            int size_ = nextInt();
            int tag = nextInt() - 1;
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
            writer.print(out.get(i)); // 这里会自动调用toString进行类型转型，log没支持这个feat
            log.info("如下为输出的命令结果: \n" + out.get(i).toString());
        }

        writer.flush();
    }

    /**
     * 从标准输入读取删除命令
     * 
     * @return 删除命令输入结构
     */
    public static ArrayList<DeleteCommandIn> readDeleteCommand() {
        ArrayList<DeleteCommandIn> in = new ArrayList<>();
        int size = nextInt();
        log.debug("读取到 " + size + " 个删除命令");
        for (int i = 0; i < size; i++) {
            int objId = nextInt();
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
        writer.println(size);
        for (int i = 0; i < size; i++) {
            writer.println(out.get(i).readCommandId);
        }
        writer.flush();
    }

    /**
     * 从标准输入读取读命令
     * 
     * @return 读命令输入结构列表
     */
    public static ArrayList<ReadCommandIn> readReadCommand() {
        ArrayList<ReadCommandIn> in = new ArrayList<>();
        int size = nextInt();

        for (int i = 0; i < size; i++) {
            int commandId = nextInt();
            int objId = nextInt();
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
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < Info.diskNum; i++) {
            // 输出磁头0的动作序列和跳转目标
            if (out.containsKey(i)) {
                for (int j = 0; j < 2; j++) {
                    List<Action> actions = out.get(i).actions.get(j);
                    if (actions != null && !actions.isEmpty()
                            && actions.get(0) == Action.JUMP) {
                        int target = out.get(i).jumpTargets.get(j) + 1;
                        writer.println("j " + target);
                    } else {
                        sb.setLength(0);
                        for (Action action : actions) {
                            if (action == Action.READ) {
                                sb.append('r');
                            } else if (action == Action.PASS) {
                                sb.append('p');
                            }
                        }
                        sb.append('#');
                        writer.println(sb);
                    }
                }
            } else {
                // 输出两个#
                writer.println("#");
                writer.println("#");
            }

            writer.flush();
        }
    }

    /**
     * 输出读取完成命令结果到标准输出
     * 
     * @param out 读取完成命令输出结构列表
     */
    public static void writeCompleteCommand(HashSet<CompleteCommandOut> out) {
        int size = out.size();
        writer.println(size);

        for (CompleteCommandOut completeCommandOut : out) {
            writer.println(completeCommandOut.commandId);
        }

        writer.flush();
    }

    /**
     * 输出忙碌命令结果到标准输出
     * 
     * @param out 忙碌命令输出结构列表
     */
    public static void writeBusyCommand(ArrayList<BusyCommandOut> out) {
        int size = out.size();
        writer.println(size);
        for (BusyCommandOut busyCommandOut : out) {
            writer.println(busyCommandOut.commandId);
        }
        writer.flush();
    }

    /**
     * 输出全部命令
     */
    public static void flushAll() {
        writer.flush();
    }

    /**
     * 读入g数组
     */
    public static void readGArray() {
        int size = Info.tickNums / FRE_PER_SLICING + 1;
        for (int i = 0; i < size; i++) {
            int g = nextInt();
            Info.g.add(g);
        }
    }
}
