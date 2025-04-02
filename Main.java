// main.java

import java.util.ArrayList;
import Deleter.Deleter;
import IO.IO;
import IO.Preprocess.Preprocess;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import IO.model.ReadCommandIn;
import IO.model.WriteCommandIn;
import IO.model.WriteCommandOut;
import IO.model.ReadRetrun;
import Info.Info;
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

    public static void main(String[] args) {
        // 配置日志记录器
        logger.setLevel(Logger.Level.ERROR);
        // logger.setLevel(Logger.Level.DEBUG);
        logger.enableModule("Main");
        logger.enableModule("Writer");
        logger.enableModule("Deleter");
        logger.enableModule("Info");
        logger.enableModule("DiskGA");
        logger.enableModule("IO");
        logger.enableModule("TagDistribution");
        // logger.enableModule("Reader");
        logger.enableModule("GAForRank");
        // 启用文件日志
        logger.enableFileLogging("logs/app.log");
        // 设置在特定时间片范围内启用详细日志
        logger.enableTimeRange(0, 0);

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

        // 设置分布和标签路径
        Preprocess.setDistributionPaths(loadDistributionPath);

        // 执行预处理
        Preprocess.preprocess();

        // 初始化策略
        Deleter deleter = new Deleter("tag");
        Writer writer = new Writer("tag");
        Reader reader = new Reader("default");

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
            IO.writeReadCommand(readRetrun.readCommandOuts);
            IO.writeCompleteCommand(readRetrun.completeCommandOuts);

            mainLogger.debug("完成处理时间片 " + i);
        }

        mainLogger.info("程序执行完毕");

        // 程序结束前关闭文件日志
        logger.disableFileLogging();
        logger.disableFileLogging();
    }
}