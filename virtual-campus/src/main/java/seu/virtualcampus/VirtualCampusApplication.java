package seu.virtualcampus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.nio.charset.StandardCharsets;

/**
 * 虚拟校园应用程序主类。
 * <p>
 * 这是Spring Boot应用程序的入口点，负责启动整个应用。
 * 它还包含了对日志系统进行UTF-8编码配置的初始化代码，以确保日志中的中文字符能够正确显示。
 */
@SpringBootApplication
@EnableTransactionManagement // 开启声明式事务支持
@EnableCaching             // 开启缓存支持
public class VirtualCampusApplication {

	/**
	 * 应用程序的启动主方法。
	 * <p>
	 * 此方法首先配置日志处理器以使用UTF-8编码，防止控制台输出中文乱码。
	 * 然后，它调用 SpringApplication.run() 来启动Spring Boot应用。
	 *
	 * @param args 传递给应用程序的命令行参数。
	 */
	public static void main(String[] args) {
		// 设置日志编码为UTF-8，防止中文乱码
		LogManager logManager = LogManager.getLogManager();
		Logger rootLogger = logManager.getLogger("");
		for (Handler handler : rootLogger.getHandlers()) {
			if (handler instanceof ConsoleHandler) {
				try {
					handler.setEncoding(StandardCharsets.UTF_8.name());
					handler.setFormatter(new SimpleFormatter());
				} catch (Exception e) {
					System.err.println("设置日志编码失败: " + e.getMessage());
				}
			}
		}
		SpringApplication.run(VirtualCampusApplication.class, args);
	}

}
