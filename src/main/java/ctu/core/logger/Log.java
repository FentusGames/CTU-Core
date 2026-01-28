package ctu.core.logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Lightweight debug logger with a hard runtime gate.
 *
 * Debug logging is OFF by default. Enable with JVM args:
 *
 * -Dctu.debug=true -Dctu.log.mute.words=word1,word2,word3 -Dctu.log.mute.packages=core.listeners,core.packets
 *
 * When disabled: - No SLF4J lookup - No reflection - Zero runtime cost
 *
 * @author Fentus
 */
public class Log {
	private static Object logger;

	private static final boolean DEBUG_ENABLED = Boolean.parseBoolean(System.getProperty("ctu.debug", "false"));

	private static final Set<String> mutedWords = new CopyOnWriteArraySet<>();
	private static final Set<String> mutedPackages = new CopyOnWriteArraySet<>();

	static {
		if (DEBUG_ENABLED) {
			try {
				Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
				Method getLoggerMethod = loggerFactoryClass.getMethod("getLogger", String.class);

				logger = getLoggerMethod.invoke(null, Log.class.getName());
			} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				logger = null;
			}

			String words = System.getProperty("ctu.log.mute.words", "");
			if (!words.isEmpty()) {
				for (String word : words.split(",")) {
					mutedWords.add(word.trim().toLowerCase());
				}
			}

			String packages = System.getProperty("ctu.log.mute.packages", "");
			if (!packages.isEmpty()) {
				for (String pkg : packages.split(",")) {
					mutedPackages.add(pkg.trim());
				}
			}
		} else {
			logger = null;
		}
	}

	public static void muteWord(String word) {
		mutedWords.add(word.toLowerCase());
	}

	public static void unmuteWord(String word) {
		mutedWords.remove(word.toLowerCase());
	}

	public static void mutePackage(String packageName) {
		mutedPackages.add(packageName);
	}

	public static void unmutePackage(String packageName) {
		mutedPackages.remove(packageName);
	}

	public static void clearMutes() {
		mutedWords.clear();
		mutedPackages.clear();
	}

	public static void debug(String message) {
		if (!DEBUG_ENABLED || logger == null) {
			return;
		}

		if (isMuted(message)) {
			return;
		}

		try {
			Method debugMethod = logger.getClass().getMethod("debug", String.class);

			debugMethod.invoke(logger, message);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			// ignore
		}
	}

	private static boolean isMuted(String message) {
		if (!mutedWords.isEmpty()) {
			String lowerMessage = message.toLowerCase();

			for (String word : mutedWords) {
				if (lowerMessage.contains(word)) {
					return true;
				}
			}
		}

		if (!mutedPackages.isEmpty()) {
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();

			if (stack.length > 3) {
				String callerClass = stack[3].getClassName();

				for (String pkg : mutedPackages) {
					if (callerClass.startsWith(pkg)) {
						return true;
					}
				}
			}
		}

		return false;
	}
}