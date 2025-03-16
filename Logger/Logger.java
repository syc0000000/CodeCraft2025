package Logger;

/**
 * 日志记录器接口
 * 支持按模块启用和按时间片启用
 */
public interface Logger {
    /**
     * 日志级别枚举
     */
    enum Level {
        DEBUG(0, "DEBUG"),
        INFO(1, "INFO"),
        WARN(2, "WARN"),
        ERROR(3, "ERROR");

        private final int value;
        private final String name;

        Level(int value, String name) {
            this.value = value;
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 记录调试级别日志
     * 
     * @param module  模块名称
     * @param message 日志消息
     */
    void debug(String module, String message);

    /**
     * 记录信息级别日志
     * 
     * @param module  模块名称
     * @param message 日志消息
     */
    void info(String module, String message);

    /**
     * 记录警告级别日志
     * 
     * @param module  模块名称
     * @param message 日志消息
     */
    void warn(String module, String message);

    /**
     * 记录错误级别日志
     * 
     * @param module  模块名称
     * @param message 日志消息
     */
    void error(String module, String message);

    /**
     * 启用指定模块的日志记录
     * 
     * @param module 模块名称
     */
    void enableModule(String module);

    /**
     * 禁用指定模块的日志记录
     * 
     * @param module 模块名称
     */
    void disableModule(String module);

    /**
     * 启用在指定时间片范围内的日志记录
     * 
     * @param startTimestamp 开始时间戳
     * @param endTimestamp   结束时间戳
     */
    void enableTimeRange(int startTimestamp, int endTimestamp);

    /**
     * 禁用时间片范围限制
     */
    void disableTimeRange();

    /**
     * 设置日志级别
     * 
     * @param level 日志级别
     */
    void setLevel(Level level);

    /**
     * 启用文件日志记录
     * 
     * @param filePath 日志文件路径
     */
    void enableFileLogging(String filePath);

    /**
     * 禁用文件日志记录
     */
    void disableFileLogging();
}