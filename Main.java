// main.java

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import Deleter.Deleter;
import IO.IO;
import IO.model.DeleteCommandIn;
import IO.model.DeleteCommandOut;
import IO.model.PreprocessOut;
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
        logger.setLevel(Logger.Level.DEBUG);
        logger.enableModule("Main");
        // logger.enableModule("Writer");
        // logger.enableModule("Deleter");
        // logger.enableModule("Info");
        logger.enableModule("Reader");
        // 启用文件日志
        logger.enableFileLogging("logs/app.log");

        mainLogger.info("程序启动");

        // 读取输入数据
        PreprocessOut preprocessOut = IO.preprocess();

        // 初始化系统
        Info.initFromPreprocessOut(preprocessOut);
        mainLogger.info(String.format("系统初始化: 硬盘数=%d, 单元数=%d, 时间片数=%d",
                Info.diskNum, Info.unitNum, Info.tickNums));

        // 初始化策略
        Deleter deleter = new Deleter("default");
        // Writer writer = new Writer("default");
        Writer writer = new Writer("rw");
        Reader reader = new Reader("newReader");
        // 设置在特定时间片范围内启用详细日志
        logger.enableTimeRange(100, 100);
        logger.enableModule("IO");

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
    }
}