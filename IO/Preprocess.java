package IO;

import java.util.ArrayList;
import java.util.Scanner;

import IO.model.PreprocessOut;
import Info.Info;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class Preprocess {
    private static final ModuleLogger log = LoggerFactory.getLogger("Preprocess");
    private static final Scanner scanner = new Scanner(System.in);
    private static final int FRE_PER_SLICING = 1800;
    // 一级key是tagId，二级key是periodIdx

    // 基本参数
    private static int T; // 总tick数
    private static int M; // 标签总数
    private static int N; // 硬盘个数
    private static int V; // 每个硬盘存储单元数
    private static int G; // 每tick Token数
    private static int K; // 垃圾回收操作次数

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

        // 新建preprocessOut
        PreprocessOut preprocessOut = new PreprocessOut();
        preprocessOut.T = T;
        preprocessOut.M = M;
        preprocessOut.N = N;
        preprocessOut.V = V;
        preprocessOut.G = G;
        preprocessOut.K = K;
        // 初始化Info模块
        Info.initFromPreprocessOut(preprocessOut, fre_read, fre_write, fre_del);

        System.out.println("OK");
        IO.flushAll();
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
     * 读取基本参数
     */
    private static void readBasicParameters() {
        T = scanner.nextInt();
        M = scanner.nextInt();
        N = scanner.nextInt();
        V = scanner.nextInt();
        G = scanner.nextInt();
        K = scanner.nextInt();
        log.info("读取基本参数: 总Tick = " + T + ", 标签数 = " + M + ", 硬盘数 = " + N + ", 每个硬盘存储单元数 = " + V
                + ", 每tick Token数 = " + G + ", 垃圾回收操作次数 = " + K);
    }
}
