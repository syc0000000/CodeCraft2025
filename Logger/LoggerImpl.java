package Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import Info.Info;

/**
 * Logger实现类
 */
public class LoggerImpl implements Logger {
    private static final LoggerImpl INSTANCE = new LoggerImpl();
    private Level level = Level.INFO;
    private final Set<String> enabledModules = new HashSet<>();
    private boolean timeRangeEnabled = false;
    private int startTimestamp = 0;
    private int endTimestamp = Integer.MAX_VALUE;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // 文件日志相关
    private boolean fileLoggingEnabled = false;
    private String logFilePath = "app.log";
    private PrintWriter fileWriter = null;

    private LoggerImpl() {
        // 私有构造函数，防止外部实例化
    }

    /**
     * 获取Logger单例
     */
    public static LoggerImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public void debug(String module, String message) {
        log(Level.DEBUG, module, message);
    }

    @Override
    public void info(String module, String message) {
        log(Level.INFO, module, message);
    }

    @Override
    public void warn(String module, String message) {
        log(Level.WARN, module, message);
    }

    @Override
    public void error(String module, String message) {
        log(Level.ERROR, module, message);
    }

    @Override
    public void enableModule(String module) {
        enabledModules.add(module);
    }

    @Override
    public void disableModule(String module) {
        enabledModules.remove(module);
    }

    @Override
    public void enableTimeRange(int startTimestamp, int endTimestamp) {
        this.timeRangeEnabled = true;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
    }

    @Override
    public void disableTimeRange() {
        this.timeRangeEnabled = false;
    }

    @Override
    public void setLevel(Level level) {
        this.level = level;
    }

    @Override
    public void enableFileLogging(String filePath) {
        try {
            // 确保目录存在
            Files.createDirectories(Paths.get(filePath).getParent());

            // 关闭之前的文件写入器（如果存在）
            if (fileWriter != null) {
                fileWriter.close();
            }

            // 创建新的文件写入器，append模式设为true
            fileWriter = new PrintWriter(new FileWriter(filePath, true));
            fileLoggingEnabled = true;
            logFilePath = filePath;

            // 记录启用文件日志的信息
            System.err.println(LocalDateTime.now().format(formatter) + " [SYSTEM] [Logger] 文件日志已启用: " + filePath);
        } catch (IOException e) {
            System.err.println(LocalDateTime.now().format(formatter) + " [ERROR] [Logger] 无法创建日志文件: " + e.getMessage());
            fileLoggingEnabled = false;
        }
    }

    @Override
    public void disableFileLogging() {
        if (fileWriter != null) {
            fileWriter.close();
            fileWriter = null;
        }
        fileLoggingEnabled = false;
        System.err.println(LocalDateTime.now().format(formatter) + " [SYSTEM] [Logger] 文件日志已禁用");
    }

    /**
     * 判断指定模块是否启用日志
     */
    private boolean isModuleEnabled(String module) {
        return enabledModules.isEmpty() || enabledModules.contains(module);
    }

    /**
     * 判断当前时间片是否在启用范围内
     */
    private boolean isTimeEnabled() {
        return !timeRangeEnabled || (Info.timestamp >= startTimestamp && Info.timestamp <= endTimestamp);
    }

    /**
     * 记录日志的核心方法
     */
    private void log(Level messageLevel, String module, String message) {
        // 级别过滤
        if (messageLevel.getValue() < level.getValue()) {
            return;
        }

        // 模块过滤
        if (!isModuleEnabled(module)) {
            return;
        }

        // 时间片过滤
        if (!isTimeEnabled()) {
            return;
        }

        // 格式化日志消息
        String currentTime = LocalDateTime.now().format(formatter);
        String timestamp = "[" + Info.timestamp + "]";
        String levelStr = "[" + messageLevel.getName() + "]";
        String moduleStr = "[" + module + "]";
        String logMessage = currentTime + " " + timestamp + " " + levelStr + " " + moduleStr + " " + message;

        // 输出到stderr
        System.err.println(logMessage);

        // 输出到文件（如果启用）
        if (fileLoggingEnabled && fileWriter != null) {
            try {
                fileWriter.println(logMessage);
                fileWriter.flush(); // 确保立即写入文件
            } catch (Exception e) {
                System.err.println(
                        LocalDateTime.now().format(formatter) + " [ERROR] [Logger] 写入日志文件失败: " + e.getMessage());
            }
        }
    }
}