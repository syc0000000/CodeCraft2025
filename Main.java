// main.java

import java.util.ArrayList;
import Deleter.Deleter;
import GC.GabageCollection;
import IO.IO;
import IO.Preprocess;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import IO.model.ReadCommandIn;
import IO.model.ReadRetrun;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import Info.Info;
import Logger.Logger;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;
import MultiReader.MultiReader;
import Writer.Writer;

/**
 * 主类，负责程序的主要流程控制
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger();
    private static final ModuleLogger mainLogger = LoggerFactory.getLogger("Main");

    public static void main(String[] args) {
        // 处理命令行参数
        processCommandLineArgs(args);

        // 配置日志记录器
        logger.setLevel(Logger.Level.DEBUG);
        // logger.setLevel(Logger.Level.DEBUG);
        // logger.enableModule("Main");
        // logger.enableModule("Preprocess");
        // logger.enableModule("Writer");
        // logger.enableModule("Deleter");
        // logger.enableModule("Info");
        // logger.enableModule("DiskGA");
        logger.enableModule("IO");
        // logger.enableModule("TagDistribution");
        logger.enableModule("Reader");
        // logger.enableModule("GAForRank");
        logger.enableModule("GC2");
        // 启用文件日志
        logger.enableFileLogging("logs/app.log");
        // 设置在特定时间片范围内启用详细日志
        logger.enableTimeRange(3600, 3603);

        mainLogger.info("程序启动");

        // 解析命令行参数，获取分布和标签路径
        String loadDistributionPath = null;

        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-load") && i + 1 < args.length) {
                loadDistributionPath = args[i + 1];
                i++;
            }
        }

        // 执行预处理
        Preprocess.preprocess();
        // Export readSizeByPeriod data to CSV
        // Info.exportTagInfoToCSV();
        mainLogger.debug(Info.tagInfoString());

        // 初始化策略
        Deleter deleter = new Deleter("tag");
        Writer writer = new Writer("MTGA");
        MultiReader reader = new MultiReader("range");

        // 主循环 - 处理每个时间片
        for (int i = 1; i <= Info.tickNums + 105; i++) {
            Info.timestamp = i; // 更新当前时间戳

            mainLogger.debug("开始处理时间片 " + i);

            // 处理时间戳
            IO.processTimeStamp();
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
            IO.writeMultiReadCommand(readRetrun.readCommandOuts);
            IO.writeCompleteCommand(readRetrun.completeCommandOuts);
            IO.writeBusyCommand(readRetrun.busyCommandOuts);
            // 每1800个时间片执行一次垃圾回收
            if (i % 1800 == 0) {
                IO.writeGCCommand(GabageCollection.entry());
                // IO.processGC();
            }

            mainLogger.debug("完成处理时间片 " + i);
        }

        mainLogger.info("程序执行完毕");

        // 程序结束前关闭文件日志
        logger.disableFileLogging();
        logger.disableFileLogging();
    }

    /**
     * 处理命令行参数
     * 
     * @param args 命令行参数数组
     */
    private static void processCommandLineArgs(String[] args) {
        // 检查是否有传递period和tags参数
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-period") && i < args.length - 1) {
                System.setProperty("period", args[i + 1]);
            } else if (args[i].equals("-tags") && i < args.length - 1) {
                System.setProperty("tags", args[i + 1]);
            }
        }
    }
}
