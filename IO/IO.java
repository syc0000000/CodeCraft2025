package IO;

import java.util.List;
import java.util.Map;
import IO.model.*;

/**
 * IO模块的主要接口
 * 只负责输入输出的解析和格式化，不负责业务逻辑处理
 */
public class IO {

    /**
     * 预处理
     * 
     * @return 预处理输出结构
     */
    public static PreprocessOut preprocess() {
        return null;
    }

    /**
     * 处理时间戳（特殊情况，直接处理）
     */
    public static void processTimeStamp() {
    }

    /**
     * 从标准输入读取写命令
     * 
     * @return 写命令输入结构列表
     */
    public static List<WriteCommandIn> readWriteCommand() {
        return null;
    }

    /**
     * 输出写命令结果到标准输出
     * 
     * @param out 写命令输出结构列表
     */
    public static void writeWriteCommand(List<WriteCommandOut> out) {
    }

    /**
     * 从标准输入读取删除命令
     * 
     * @return 删除命令输入结构
     */
    public static DeleteCommandIn readDeleteCommand() {
        return null;
    }

    /**
     * 输出删除命令结果到标准输出
     * 
     * @param out 删除命令输出结构
     */
    public static void writeDeleteCommand(DeleteCommandOut out) {
    }

    /**
     * 从标准输入读取读命令
     * 
     * @return 读命令输入结构列表
     */
    public static List<ReadCommandIn> readReadCommand() {
        return null;
    }

    /**
     * 输出读命令结果到标准输出
     * 
     * @param out 读命令输出结构，key为磁盘ID，value为读命令输出结构
     * @note 只需要放动了的命令，如果磁头完全不动，则不需要传入
     */
    public static void writeReadCommand(Map<Integer, ReadCommandOut> out) {
    }

    /**
     * 输出读取完成命令结果到标准输出
     * 
     * @param out 读取完成命令输出结构列表
     */
    public static void writeCompleteCommand(List<CompleteCommandOut> out) {
    }

    /**
     * 输出全部命令
     */
    public static void flushAll() {
    }
}