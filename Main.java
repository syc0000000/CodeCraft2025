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

    public static void main(String[] args) {
        // 配置日志记录器
        logger.setLevel(Logger.Level.DEBUG);
        // logger.setLevel(Logger.Level.DEBUG);
        logger.enableModule("Main");
        // logger.enableModule("Writer");
        // logger.enableModule("Deleter");
        logger.enableModule("Info");
        logger.enableModule("DiskGA");
        logger.enableModule("IO");
        logger.enableModule("Reader");
        logger.enableModule("GAForRank");
        // 启用文件日志
        logger.enableFileLogging("logs/app.log");
        // 设置在特定时间片范围内启用详细日志
        logger.enableTimeRange(0, 0);

        mainLogger.info("程序启动");

        // 读取输入数据
        PreprocessOut preprocessOut = IO.preprocess();

        // 初始化系统
        Info.initFromPreprocessOut(preprocessOut);
        // 默认每次生成新文件，格式：distributions/distribution_yyyyMMdd_HHmmss.ser
        String loadPath = null; // 如果指定 -load 参数，则从此文件读取

        // 解析命令行参数
        if (args.length > 0 && args[0].equals("-load")) {
            if (args.length < 2) {
                mainLogger.error("请指定要加载的文件路径，例如: -load distributions/latest.ser");
                return;
            }
            loadPath = args[1];
        }

        int[] tagValues = IO.tagsUnitUsage.stream().mapToInt(Integer::intValue).toArray();
        Map<Integer, List<DiskDistributionGA.Split>> distribution; // 一级Map的key是tagId，二级Map的key无意义，value是某磁盘分配百分比

        if (loadPath != null) {
            // 从指定文件加载
            distribution = loadDistribution(loadPath);
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
        // 使用遗传算法对每个磁盘的tag进行排序
        for (int i = 0; i < Info.diskNum; i++) {
            if (diskToTag.get(i).isEmpty()) {
                continue;
            }
            long startTime = System.currentTimeMillis();

            ArrayList<Integer> sortedTagIds = Entry.entrypoint(diskToTag.get(i), i);

            long endTime = System.currentTimeMillis();
            mainLogger.info("磁盘 " + i + " 标签排序完成，耗时:" + (endTime - startTime) + "ms");

            // 记录排序结果到Tag的middle位置
            int currentPosition = 0;
            for (Integer tagId : sortedTagIds) {
                Info.Tag tag = Info.tags.get(tagId);
                int tagSize = tag.lenthList.get(i);

                // 记录标签在磁盘上的中间位置
                tag.middleList.set(i, currentPosition + tagSize / 2);
                currentPosition += tagSize;
            }
        }
        // 转化tag结果为middle位置，写入Tag中

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
}