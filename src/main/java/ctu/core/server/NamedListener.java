package ctu.core.server;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import ctu.core.interfaces.Listener;
import ctu.core.logger.Log;

/**
 * A listener bound to a single dedicated worker thread that drains its queue
 * sequentially (preserving per-listener event order).
 *
 * Watchdog: every cache/auth handshake on the lobby runs on this one thread, so
 * a single task that blocks forever (e.g. a JDBC read on a silently-dead socket,
 * or sendTCP backpressure to a stalled client) would otherwise wedge the whole
 * listener until the JVM restarts. A shared daemon watchdog detects a task that
 * has been running longer than {@link #STUCK_THRESHOLD_MS}, logs the stuck
 * thread's stack, and spawns a replacement worker so the listener resumes. The
 * wedged thread exits on its next loop check (e.g. once the JDBC socketTimeout
 * fires) instead of competing with the replacement for the queue.
 */
public class NamedListener<T> {
	/**
	 * A task running longer than this is treated as wedged. Kept above the
	 * pgjdbc socketTimeout (~30s) so the cheap, self-recovering JDBC failure
	 * happens first; the heavier thread-recycle is the true last resort for
	 * non-JDBC hangs (infinite loops, outbound-buffer backpressure stalls).
	 */
	private static final long STUCK_THRESHOLD_MS = 60_000;

	/** How often the watchdog samples worker liveness. */
	private static final long CHECK_INTERVAL_MS = 15_000;

	/** One shared daemon scheduler watches every listener worker process-wide. */
	private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "NamedListener-Watchdog");
		t.setDaemon(true);
		return t;
	});

	final Listener<T> listener;
	final String name;

	private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

	private volatile boolean running = true;

	/** Identifies the active worker; stale (recycled) workers exit when this changes. */
	private final AtomicInteger generation = new AtomicInteger(0);

	/** State of the currently-active worker. Replaced atomically on recycle. */
	private final AtomicReference<WorkerState> active = new AtomicReference<>();

	private ScheduledFuture<?> watchdogTask;

	NamedListener(Listener<T> listener, String name) {
		this.listener = listener;
		this.name = name;
	}

	/** Per-worker liveness state. Each worker owns its own; recycled ones are detached and ignored. */
	private static final class WorkerState {
		final Thread thread;
		/** Start time (ms) of the task currently in r.run(), or 0 when idle. */
		volatile long taskStartMs = 0;

		WorkerState(Thread thread) {
			this.thread = thread;
		}
	}

	void start() {
		spawnWorker(generation.get());
		watchdogTask = WATCHDOG.scheduleAtFixedRate(this::checkStuck, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	private void spawnWorker(int myGeneration) {
		final WorkerState[] holder = new WorkerState[1];

		Thread thread = new Thread(() -> {
			WorkerState state = holder[0];

			while (running && myGeneration == generation.get()) {
				Runnable r;
				try {
					r = queue.take();
				} catch (InterruptedException e) {
					if (!running) {
						break;
					}
					continue;
				}

				state.taskStartMs = System.currentTimeMillis();
				try {
					r.run();
				} catch (Throwable t) {
					Log.error("Listener worker [" + name + "] crashed", t);
				} finally {
					state.taskStartMs = 0;
				}
			}
		});

		thread.setName(myGeneration == 0 ? name : name + "#gen" + myGeneration);
		thread.setDaemon(true);

		WorkerState state = new WorkerState(thread);
		holder[0] = state;
		active.set(state);

		thread.start();
	}

	/**
	 * Watchdog tick. Must never throw: a thrown exception silently cancels all
	 * future executions of a scheduled task.
	 */
	private void checkStuck() {
		try {
			if (!running) {
				return;
			}

			WorkerState state = active.get();
			if (state == null) {
				return;
			}

			long startedAt = state.taskStartMs;
			if (startedAt == 0) {
				return; // worker idle / healthy
			}

			long stuckForMs = System.currentTimeMillis() - startedAt;
			if (stuckForMs < STUCK_THRESHOLD_MS) {
				return;
			}

			StringBuilder where = new StringBuilder();
			Thread stuck = state.thread;
			if (stuck != null) {
				for (StackTraceElement el : stuck.getStackTrace()) {
					where.append("\n\tat ").append(el);
				}
			}

			Log.error("Listener worker [" + name + "] wedged for " + stuckForMs + "ms on a single task; spawning a replacement so [" + name + "] can resume. Wedged thread stack:" + where);

			// Advance generation so the wedged thread exits on its next loop
			// check (instead of racing the replacement for the queue), then
			// start a fresh worker to drain the backlog.
			int next = generation.incrementAndGet();
			spawnWorker(next);
		} catch (Throwable t) {
			Log.error("Listener watchdog [" + name + "] check failed", t);
		}
	}

	void enqueue(Runnable r) {
		if (!running) {
			return;
		}

		queue.offer(r);
	}

	void shutdown() {
		running = false;

		if (watchdogTask != null) {
			watchdogTask.cancel(false);
		}

		WorkerState state = active.get();
		if (state != null && state.thread != null) {
			state.thread.interrupt();
		}

		queue.clear();
	}
}
