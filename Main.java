// main.java

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import Deleter.Deleter;
import IO.IO;
import IO.GAForRank.Entry;
import IO.model.DiskDistributionGA;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import IO.model.PreprocessOut;
import IO.model.ReadCommandIn;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import IO.model.ReadRetrun;
import Info.Info;
import Info.Info.Tag;
import Logger.Logger;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;
import Writer.Writer;
import Reader.Reader;

/**
 * 主类，负责程序的主要流程控制
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger();
    private static final ModuleLogger mainLogger = LoggerFactory.getLogger("Main");

    private static Map<Integer, List<DiskDistributionGA.Split>> computeDistribution(int[] tagValues, boolean isGA) {
        Map<Integer, List<DiskDistributionGA.Split>> distribution = new HashMap<>();
        if (isGA) {
            distribution = DiskDistributionGA.entrypoint(tagValues);
        } else {
            ArrayList<DiskDistributionGA.Split> splits = new ArrayList<>();
            for (int i = 0; i < Info.diskNum; i++) {
                splits.add(new DiskDistributionGA.Split(i, 10));
            }
            for (int i = 0; i < tagValues.length; i++) {
                distribution.put(i, splits);
            }
        }
        return distribution;
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, List<DiskDistributionGA.Split>> loadDistribution(String path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            return (Map<Integer, List<DiskDistributionGA.Split>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            mainLogger.error("加载失败: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    private static void saveDistribution(Map<Integer, List<DiskDistributionGA.Split>> distribution, String path) {
        File file = new File(path);
        try {
            file.getParentFile().mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(distribution);
                mainLogger.info("已保存到: " + path);
            }
        } catch (IOException e) {
            mainLogger.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 从文件加载标签排序结果
     * 
     * @param path 文件路径
     * @return 磁盘ID到排序后标签列表的映射
     */
    @SuppressWarnings("unchecked")
    private static Map<Integer, ArrayList<Integer>> loadSortedTags(String path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            return (Map<Integer, ArrayList<Integer>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            mainLogger.error("加载标签排序失败: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    /**
     * 保存标签排序结果到文件
     * 
     * @param sortedTags 磁盘ID到排序后标签列表的映射
     * @param path       保存路径
     */
    private static void saveSortedTags(Map<Integer, ArrayList<Integer>> sortedTags, String path) {
        File file = new File(path);
        try {
            file.getParentFile().mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(sortedTags);
                mainLogger.info("标签排序已保存到: " + path);
            }
        } catch (IOException e) {
            mainLogger.error("保存标签排序失败: " + e.getMessage());
        }
    }

    /**
     * 将排序后的标签结果应用到Tag中间位置
     * 
     * @param sortedTags 磁盘ID到排序后标签列表的映射
     */
    private static void applySortedTags(Map<Integer, ArrayList<Integer>> sortedTags) {
        for (Map.Entry<Integer, ArrayList<Integer>> entry : sortedTags.entrySet()) {
            int diskId = entry.getKey();
            ArrayList<Integer> sortedTagIds = entry.getValue();

            mainLogger.info("应用磁盘 " + diskId + " 的标签排序，共 " + sortedTagIds.size() + " 个标签");

            // 记录排序结果到Tag的middle位置
            int currentPosition = 0;
            for (Integer tagId : sortedTagIds) {
                Info.Tag tag = Info.tags.get(tagId);
                int tagSize = tag.lenthList.get(diskId);

                // 记录标签在磁盘上的中间位置
                tag.middleList.set(diskId, currentPosition + tagSize / 2);
                currentPosition += tagSize;
            }
        }
    }

    /**
     * 并行处理每个磁盘的标签排序
     * 
     * @param diskToTag 每个磁盘的标签集合
     * @return 磁盘ID到排序后标签列表的映射
     */
    private static Map<Integer, ArrayList<Integer>> parallelSortTagsForDisks(ArrayList<HashSet<Integer>> diskToTag) {
        // 创建线程池，使用可用处理器数量
        int processors = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(processors);
        mainLogger.info("创建线程池，使用 " + processors + " 个线程进行标签排序");

        // 为每个磁盘创建一个任务
        List<Future<DiskSortResult>> futures = new ArrayList<>();

        long overallStartTime = System.currentTimeMillis();

        // 提交所有任务到线程池
        submitSortTasks(diskToTag, executor, futures);

        // 处理所有任务结果并返回排序结果
        Map<Integer, ArrayList<Integer>> sortedTags = processTaskResults(futures);

        long overallEndTime = System.currentTimeMillis();
        long totalTime = overallEndTime - overallStartTime;

        mainLogger.info("所有磁盘的标签排序已完成，总耗时: " + totalTime + "ms");

        // 关闭线程池
        executor.shutdown();

        return sortedTags;
    }

    /**
     * 提交所有磁盘的排序任务到线程池
     */
    private static void submitSortTasks(ArrayList<HashSet<Integer>> diskToTag,
            ExecutorService executor,
            List<Future<DiskSortResult>> futures) {
        for (int i = 0; i < Info.diskNum; i++) {
            if (diskToTag.get(i).isEmpty()) {
                continue;
            }
            final int diskId = i;

            // 提交任务到线程池
            futures.add(executor.submit(new Callable<DiskSortResult>() {
                @Override
                public DiskSortResult call() throws Exception {
                    long startTime = System.currentTimeMillis();

                    ArrayList<Integer> sortedTagIds = Entry.entrypoint(diskToTag.get(diskId), diskId);

                    long endTime = System.currentTimeMillis();
                    long timeSpent = endTime - startTime;

                    return new DiskSortResult(diskId, sortedTagIds, timeSpent);
                }
            }));
        }
    }

    /**
     * 处理所有排序任务的结果
     * 
     * @param futures 任务Future列表
     * @return 磁盘ID到排序后标签列表的映射
     */
    private static Map<Integer, ArrayList<Integer>> processTaskResults(List<Future<DiskSortResult>> futures) {
        Map<Integer, ArrayList<Integer>> sortedTags = new HashMap<>();

        for (Future<DiskSortResult> future : futures) {
            try {
                DiskSortResult result = future.get();
                int diskId = result.diskId;
                ArrayList<Integer> sortedTagIds = result.sortedTagIds;
                long timeSpent = result.timeSpent;

                mainLogger.info("磁盘 " + diskId + " 标签排序完成，耗时:" + timeSpent + "ms");

                // 保存排序结果
                sortedTags.put(diskId, sortedTagIds);

                // 记录排序结果到Tag的middle位置
                int currentPosition = 0;
                for (Integer tagId : sortedTagIds) {
                    Info.Tag tag = Info.tags.get(tagId);
                    int tagSize = tag.lenthList.get(diskId);

                    // 记录标签在磁盘上的中间位置
                    tag.middleList.set(diskId, currentPosition + tagSize / 2);
                    currentPosition += tagSize;
                }
            } catch (InterruptedException | ExecutionException e) {
                mainLogger.error("处理磁盘标签排序时出错: " + e.getMessage());
            }
        }

        return sortedTags;
    }

    public static void main(String[] args) {
        // 配置日志记录器
        logger.setLevel(Logger.Level.DEBUG);
        // logger.setLevel(Logger.Level.DEBUG);
        logger.enableModule("Main");
        logger.enableModule("Writer");
        logger.enableModule("Deleter");
        logger.enableModule("Info");
        logger.enableModule("DiskGA");
        logger.enableModule("IO");
        // logger.enableModule("Reader");
        logger.enableModule("GAForRank");
        // 启用文件日志
        logger.enableFileLogging("logs/app.log");
        // 设置在特定时间片范围内启用详细日志
        logger.enableTimeRange(51552, 60500);

        mainLogger.info("程序启动");

        // 读取输入数据
        PreprocessOut preprocessOut = IO.preprocess();

        // 初始化系统
        Info.initFromPreprocessOut(preprocessOut);
        // 默认每次生成新文件，格式：distributions/distribution_yyyyMMdd_HHmmss.ser
        String loadDistributionPath = null; // 如果指定 -load 参数，则从此文件读取
        String loadTagsPath = null; // 如果指定 -loadTags 参数，则从此文件读取标签排序

        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-load") && i + 1 < args.length) {
                loadDistributionPath = args[i + 1];
                i++;
            } else if (args[i].equals("-loadTags") && i + 1 < args.length) {
                loadTagsPath = args[i + 1];
                i++;
            }
        }

        int[] tagValues = IO.tagsUnitUsage.stream().mapToInt(Integer::intValue).toArray();
        Map<Integer, List<DiskDistributionGA.Split>> distribution; // 一级Map的key是tagId，二级Map的key无意义，value是某磁盘分配百分比

        if (loadDistributionPath != null) {
            // 从指定文件加载
            distribution = loadDistribution(loadDistributionPath);
        } else {
            // 重新计算并保存到带时间戳的文件
            distribution = computeDistribution(tagValues, true);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String savePath = "distributions/distribution_" + timestamp + ".ser";
            saveDistribution(distribution, savePath);
        }
        mainLogger.info("遗传算法分配结果: " + distribution.toString());

        // 临时变量，帮助记录每个Tag在每个Disk上的middle位置
        ArrayList<Integer> startPositionForDisk = new ArrayList<>();
        for (int i = 0; i < Info.diskNum; i++) {
            startPositionForDisk.add(0);
        }
        // 每个磁盘有哪些tag
        ArrayList<HashSet<Integer>> diskToTag = new ArrayList<>();
        for (int i = 0; i < Info.diskNum; i++) {
            diskToTag.add(new HashSet<>());
        }

        for (int i = 0; i < tagValues.length; i++) {
            Tag tag = new Info.Tag(i, tagValues[i], Info.diskNum);
            mainLogger.info("Tag " + i + " 的分配结果: " + distribution.get(i).toString());
            for (DiskDistributionGA.Split split : distribution.get(i)) {
                int diskId = split.diskIdx;
                int sizeInThisDisk = (int) Math.ceil(tagValues[i] * split.portion / 100.0);
                // lenthList的diskId位置，写入sizeInThisDisk
                tag.lenthList.set(diskId, sizeInThisDisk);
                diskToTag.get(diskId).add(i);
            }
            Info.tags.add(tag);
        }

        // 处理标签排序
        if (loadTagsPath != null) {
            // 从文件加载标签排序结果
            mainLogger.info("从文件加载标签排序: " + loadTagsPath);
            Map<Integer, ArrayList<Integer>> sortedTags = loadSortedTags(loadTagsPath);
            applySortedTags(sortedTags);
        } else {
            // 使用遗传算法对每个磁盘的tag进行排序
            Map<Integer, ArrayList<Integer>> sortedTags = parallelSortTagsForDisks(diskToTag);

            // 保存排序结果到文件
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String savePath = "tags/sortedTags_" + timestamp + ".ser";
            saveSortedTags(sortedTags, savePath);
        }

        // 初始化策略
        Deleter deleter = new Deleter("tag");
        Writer writer = new Writer("tag");
        Reader reader = new Reader("default");

        // 主循环 - 处理每个时间片
        try (FileWriter fileWriter = new FileWriter("disk0RWEnd.txt", false)) {
            for (int i = 1; i <= preprocessOut.T + 105; i++) {
                Info.timestamp = i; // 更新当前时间戳

                mainLogger.debug("开始处理时间片 " + i);

                // 处理时间戳
                IO.processTimeStamp();
                // 测量disk0 RWEnd位置和磁头位置
                int disk0RWEnd = Info.localDiskTbl.get(0).RWEnd;
                int disk0HeadPos = Info.localDiskTbl.get(0).ptr;

                // 写入文件
                fileWriter.write(String.valueOf(disk0RWEnd));
                fileWriter.write(" ");
                fileWriter.write(String.valueOf(disk0HeadPos));
                fileWriter.write(System.lineSeparator()); // 添加换行符

                // 处理删除命令
                ArrayList<DeleteCommandIn> deleteIn = IO.readDeleteCommand();
                if (!deleteIn.isEmpty()) {
                    mainLogger.info("读取到 " + deleteIn.size() + " 个删除命令");
                }
                ArrayList<DeleteCommandOut> deleteOut = deleter.delete(deleteIn);
                IO.writeDeleteCommand(deleteOut);

                // 处理写入命令
                ArrayList<WriteCommandIn> writeIn = IO.readWriteCommand();
                if (!writeIn.isEmpty()) {
                    mainLogger.info("读取到 " + writeIn.size() + " 个写入命令");
                }
                ArrayList<WriteCommandOut> writeOut = writer.write(writeIn);
                IO.writeWriteCommand(writeOut);

                // 处理读取命令
                ArrayList<ReadCommandIn> readIn = IO.readReadCommand();
                if (!readIn.isEmpty()) {
                    mainLogger.info("读取到 " + readIn.size() + " 个读取命令");
                }
                ReadRetrun readRetrun = reader.read(readIn);
                IO.writeReadCommand(readRetrun.readCommandOuts);
                IO.writeCompleteCommand(readRetrun.completeCommandOuts);

                mainLogger.debug("完成处理时间片 " + i);
            }
        } catch (IOException e) {
            mainLogger.error("写入 disk0RWEnd.txt 文件时发生错误: " + e.getMessage());
            e.printStackTrace();
        }

        mainLogger.info("程序执行完毕");

        // 程序结束前关闭文件日志
        logger.disableFileLogging();
        logger.disableFileLogging();
    }

    // 磁盘排序结果类
    private static class DiskSortResult {
        int diskId;
        ArrayList<Integer> sortedTagIds;
        long timeSpent;

        public DiskSortResult(int diskId, ArrayList<Integer> sortedTagIds, long timeSpent) {
            this.diskId = diskId;
            this.sortedTagIds = sortedTagIds;
            this.timeSpent = timeSpent;
        }
    }
}