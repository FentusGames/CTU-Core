package ctu.core.logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Lightweight logger with configurable log levels.
 *
 * Logging is OFF by default. Enable with JVM args:
 *
 * -Dctu.log.level=DEBUG
 *
 * Log levels (from lowest to highest): TRACE, DEBUG, INFO, WARN, ERROR, OFF
 *
 * @author Fentus
 */
public class Log {
	public enum Level {
		TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), OFF(5);

		private final int value;

		Level(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}

	private static Object logger;
	private static Method traceMethod;
	private static Method debugMethod;
	private static Method infoMethod;
	private static Method warnMethod;
	private static Method errorMethod;

	private static final Level LOG_LEVEL;
	private static final boolean LOGGING_ENABLED;

	static {
		String levelStr = System.getProperty("ctu.log.level", "OFF").toUpperCase();
		Level parsedLevel;

		try {
			parsedLevel = Level.valueOf(levelStr);
		} catch (IllegalArgumentException e) {
			parsedLevel = Level.OFF;
		}

		LOG_LEVEL = parsedLevel;
		LOGGING_ENABLED = LOG_LEVEL != Level.OFF;

		if (LOGGING_ENABLED) {
			try {
				Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
				Method getLoggerMethod = loggerFactoryClass.getMethod("getLogger", String.class);
				logger = getLoggerMethod.invoke(null, Log.class.getName());

				traceMethod = logger.getClass().getMethod("trace", String.class);
				debugMethod = logger.getClass().getMethod("debug", String.class);
				infoMethod = logger.getClass().getMethod("info", String.class);
				warnMethod = logger.getClass().getMethod("warn", String.class);
				errorMethod = logger.getClass().getMethod("error", String.class);
			} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				logger = null;
			}
		} else {
			logger = null;
		}
	}

	public static Level getLevel() {
		return LOG_LEVEL;
	}

	public static void trace(String message) {
		log(Level.TRACE, traceMethod, message);
	}

	public static void debug(String message) {
		log(Level.DEBUG, debugMethod, message);
	}

	public static void info(String message) {
		log(Level.INFO, infoMethod, message);
	}

	public static void warn(String message) {
		log(Level.WARN, warnMethod, message);
	}

	public static void error(String message) {
		log(Level.ERROR, errorMethod, message);
	}

	public static void error(String message, Throwable throwable) {
		error(message);
		if (throwable != null) {
			error(stackTraceToString(throwable));
		}
	}

	public static void warn(String message, Throwable throwable) {
		warn(message);
		if (throwable != null) {
			warn(stackTraceToString(throwable));
		}
	}

	public static void debug(String message, Throwable throwable) {
		debug(message);
		if (throwable != null) {
			debug(stackTraceToString(throwable));
		}
	}

	private static String stackTraceToString(Throwable throwable) {
		java.io.StringWriter sw = new java.io.StringWriter();
		throwable.printStackTrace(new java.io.PrintWriter(sw));
		return sw.toString();
	}

	private static void log(Level level, Method method, String message) {
		if (!LOGGING_ENABLED || logger == null || method == null) {
			return;
		}

		if (level.getValue() < LOG_LEVEL.getValue()) {
			return;
		}

		try {
			method.invoke(logger, message);
		} catch (IllegalAccessException | InvocationTargetException e) {
			// ignore
		}
	}
}
