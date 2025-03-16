// main.java

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import IO.IO;
import IO.model.*;
import Info.Info;

/**
 * 主类，负责程序的主要流程控制
 */
public class Main {
    public static void main(String[] args) {
        // 读取输入数据
        PreprocessOut preprocessOut = IO.preprocess();
        Info.diskNum = preprocessOut.N;
        Info.unitNum = preprocessOut.V;
        Info.tokenPerTick = preprocessOut.G;
        Info.tagNums = preprocessOut.M;
        Info.tickNums = preprocessOut.T;

        // 初始化系统
        Info.init();

        // 主循环 - 处理每个时间片
        for (int i = 1; i <= preprocessOut.T + 105; i++) {
            // 处理时间戳
            IO.processTimeStamp();

            // 处理删除命令
            ArrayList<DeleteCommandIn> deleteIn = IO.readDeleteCommand();
            // TODO: 调用删除处理逻辑
            ArrayList<DeleteCommandOut> deleteOut = new ArrayList<>();
            IO.writeDeleteCommand(deleteOut);

            // 处理写入命令
            List<WriteCommandIn> writeIn = IO.readWriteCommand();
            List<WriteCommandOut> writeOut = new ArrayList<>();
            // TODO: 调用写入处理逻辑
            IO.writeWriteCommand(writeOut);

            // 处理读取命令
            List<ReadCommandIn> readIn = IO.readReadCommand();
            // TODO: 添加新的读取任务

            // TODO: 获取读策略
            Map<Integer, ReadCommandOut> readOut = null; // 需要实际实现
            IO.writeReadCommand(readOut);

            // 处理完成命令
            List<CompleteCommandOut> completeOut = new ArrayList<>();
            // TODO: 添加完成的命令
            IO.writeCompleteCommand(completeOut);
        }
    }
}