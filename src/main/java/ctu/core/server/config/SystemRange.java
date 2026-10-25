package ctu.core.server.config;

import java.util.ArrayList;
import java.util.List;

/**
 * An inclusive run of system ids. A game shard owns a list of them, so a split
 * need not follow id order: prod's shard-1 runs only the starter system and the
 * systems it gates to.
 */
public record SystemRange(long start, long end) {
	public SystemRange {
		if (start < 1 || end < start) {
			throw new IllegalArgumentException("Bad system range " + start + "-" + end);
		}
	}

	public boolean contains(long systemId) {
		return systemId >= start && systemId <= end;
	}

	public boolean overlaps(SystemRange other) {
		return start <= other.end && other.start <= end;
	}

	/** Whether any of {@code ranges} holds {@code systemId}. */
	public static boolean anyContains(List<SystemRange> ranges, long systemId) {
		for (SystemRange range : ranges) {
			if (range.contains(systemId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A YAML {@code systemRanges:} value: a list whose items are a system id
	 * ({@code 44}) or an inclusive run ({@code 45-105}), or the same items
	 * comma-separated in one string. Anything else throws, so a typo stops the
	 * boot instead of leaving systems with no shard.
	 */
	public static List<SystemRange> parseList(Object value) {
		List<Object> items = new ArrayList<>();
		if (value instanceof List<?> list) {
			items.addAll(list);
		} else if (value instanceof Number) {
			items.add(value);
		} else {
			for (String item : String.valueOf(value).split(",")) {
				items.add(item);
			}
		}
		List<SystemRange> out = new ArrayList<>();
		for (Object item : items) {
			out.add(parse(item));
		}
		return out;
	}

	private static SystemRange parse(Object item) {
		if (item instanceof Integer || item instanceof Long) {
			long id = ((Number) item).longValue();
			return new SystemRange(id, id);
		}
		String text = String.valueOf(item).trim();
		try {
			int dash = text.indexOf('-');
			if (dash < 0) {
				long id = Long.parseLong(text);
				return new SystemRange(id, id);
			}
			return new SystemRange(Long.parseLong(text.substring(0, dash).trim()), Long.parseLong(text.substring(dash + 1).trim()));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Bad system range '" + text + "'", e);
		}
	}

	@Override
	public String toString() {
		return start == end ? Long.toString(start) : start + "-" + end;
	}
}
