package ctu.core.logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 
 * The Log class imports the logger from SLF4J if it is available or it will print nothing. The class is used for
 * logging purposes, specifically for debugging.
 * 
 * @author Fentus
 *
 */
public class Log {
	private static Object logger;

	static {
		try {
			Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
			Method getLoggerMethod = loggerFactoryClass.getMethod("getLogger", String.class);
			logger = getLoggerMethod.invoke(null, Log.class.getName());
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			// SLF4J not available, do nothing
		}
	}

	public static void debug(String message) {
		if (logger != null) {
			try {
				Method debugMethod = logger.getClass().getMethod("debug", String.class);
				debugMethod.invoke(logger, message);
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				// ignore, log method not available
			}
		}
	}
}