# Logger模块使用说明

这是一个简单的日志记录系统，支持按模块启用和按时间片启用日志记录。

## 功能特点

- 支持不同日志级别：DEBUG、INFO、WARN、ERROR
- 支持按模块启用或禁用日志输出
- 支持按时间片范围启用或禁用日志输出
- 采用单例模式，确保整个应用只有一个Logger实例
- 提供模块化日志记录，简化API调用
- 日志输出到标准错误流（stderr）
- 支持可选的文件日志记录

## 使用方法

### 1. 获取Logger实例

有两种方式获取Logger：

```java
// 获取全局Logger
Logger logger = LoggerFactory.getLogger();

// 获取特定模块的Logger
ModuleLogger moduleLogger = LoggerFactory.getLogger("ModuleName");
```

### 2. 记录日志

使用全局Logger：

```java
logger.debug("ModuleName", "调试信息");
logger.info("ModuleName", "一般信息");
logger.warn("ModuleName", "警告信息");
logger.error("ModuleName", "错误信息");
```

使用模块Logger（更简洁）：

```java
moduleLogger.debug("调试信息");
moduleLogger.info("一般信息");
moduleLogger.warn("警告信息");
moduleLogger.error("错误信息");
```

### 3. 配置Logger

设置日志级别：

```java
logger.setLevel(Logger.Level.INFO); // 只输出INFO及以上级别的日志
```

启用特定模块的日志：

```java
logger.enableModule("Main");
logger.enableModule("IO");
```

禁用特定模块的日志：

```java
logger.disableModule("IO");
```

启用特定时间片范围的日志：

```java
logger.enableTimeRange(10, 20); // 只在时间片10-20之间输出日志
```

禁用时间片限制：

```java
logger.disableTimeRange();
```

### 4. 文件日志配置

启用文件日志：

```java
logger.enableFileLogging("logs/app.log"); // 指定文件路径
```

禁用文件日志：

```java
logger.disableFileLogging();
```

## 示例

```java
// 获取Logger
Logger logger = LoggerFactory.getLogger();
ModuleLogger mainLogger = LoggerFactory.getLogger("Main");

// 配置Logger
logger.setLevel(Logger.Level.INFO);
logger.enableModule("Main");
logger.enableTimeRange(10, 20);

// 启用文件日志
logger.enableFileLogging("logs/app.log");

// 记录日志
mainLogger.info("系统启动");

// 在时间片循环中使用
for (int i = 1; i <= totalTicks; i++) {
    Info.timestamp = i; // 更新当前时间戳
    mainLogger.debug("处理时间片 " + i);
    // 业务逻辑...
}

// 禁用文件日志
logger.disableFileLogging();
``` 

## 注意事项

1. 所有日志都会输出到标准错误流（stderr）
2. 如果启用了文件日志，日志会同时写入指定的文件
3. 文件日志采用追加模式，不会覆盖已有内容
4. 确保应用有权限创建和写入指定的日志文件 