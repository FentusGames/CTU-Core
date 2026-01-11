package ctu.core.server;

import ctu.core.interfaces.Listener;
import ctu.core.logger.Log;

public class NamedListener<T> {
	final Listener<T> listener;
	final String name;

	private final java.util.concurrent.LinkedBlockingQueue<Runnable> queue = new java.util.concurrent.LinkedBlockingQueue<>();

	private volatile boolean running = true;
	private Thread thread;

	NamedListener(Listener<T> listener, String name) {
		this.listener = listener;
		this.name = name;
	}

	void start() {
		thread = new Thread(() -> {
			while (running) {
				try {
					Runnable r = queue.take();
					r.run();
				} catch (InterruptedException e) {
					if (!running) {
						break;
					}
					Thread.currentThread().interrupt();
				} catch (Throwable t) {
					Log.debug("Listener worker [" + name + "] crashed: " + t.getMessage());
					t.printStackTrace();
				}
			}
		});

		thread.setName(name);
		thread.setDaemon(true);
		thread.start();
	}

	void enqueue(Runnable r) {
		if (!running) {
			return;
		}

		queue.offer(r);
	}

	void shutdown() {
		running = false;
		if (thread != null) {
			thread.interrupt();
		}

		queue.clear();
	}
}