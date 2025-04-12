package Logger;

/**
 * Logger工厂类
 * 提供获取Logger实例的静态方法
 */
public class LoggerFactory {
    /**
     * 获取Logger实例
     * 
     * @return Logger实例
     */
    public static Logger getLogger() {
        return LoggerImpl.getInstance();
    }

    /**
     * 获取带有指定模块的Logger代理
     * 简化日志调用，不需要每次都指定模块名
     * 
     * @param module 模块名称
     * @return 模块特定的Logger代理
     */
    public static ModuleLogger getLogger(String module) {
        return new ModuleLogger(module);
    }

    /**
     * 模块特定的Logger代理类
     * 封装了模块名称，简化日志调用
     */
    public static class ModuleLogger {
        private final String module;
        private final Logger logger;

        ModuleLogger(String module) {
            this.module = module;
            this.logger = LoggerImpl.getInstance();
        }

        /**
         * 记录调试级别日志
         * 
         * @param message 日志消息
         */
        public void debug(String message) {
            logger.debug(module, message);
        }

        /**
         * 记录信息级别日志
         * 
         * @param message 日志消息
         */
        public void info(String message) {
            logger.info(module, message);
        }

        /**
         * 记录警告级别日志
         * 
         * @param message 日志消息
         */
        public void warn(String message) {
            logger.warn(module, message);
        }

        /**
         * 记录错误级别日志
         * 
         * @param message 日志消息
         */
        public void error(String message) {
            logger.error(module, message);
        }
    }
}