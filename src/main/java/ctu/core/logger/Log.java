package ctu.core.logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Lightweight debug logger with a hard runtime gate.
 *
 * Debug logging is OFF by default. Enable with JVM arg:
 *
 * -Dctu.debug=true
 *
 * When disabled: - No SLF4J lookup - No reflection - Zero runtime cost
 *
 * @author Fentus
 */
public class Log {
	private static Object logger;

	// Global debug toggle (read once at class load)
	private static final boolean DEBUG_ENABLED = Boolean.parseBoolean(System.getProperty("ctu.debug", "false"));

	static {
		if (DEBUG_ENABLED) {
			try {
				Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
				Method getLoggerMethod = loggerFactoryClass.getMethod("getLogger", String.class);

				logger = getLoggerMethod.invoke(null, Log.class.getName());
			} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				// SLF4J not available → disable logging silently
				logger = null;
			}
		} else {
			// Explicitly disabled
			logger = null;
		}
	}

	public static void debug(String message) {
		if (!DEBUG_ENABLED || logger == null) {
			return;
		}

		try {
			Method debugMethod = logger.getClass().getMethod("debug", String.class);

			debugMethod.invoke(logger, message);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			// ignore
		}
	}
}
