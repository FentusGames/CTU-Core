package ctu.core.abstracts;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ctu.core.Config;
import ctu.core.interfaces.Listener;
import ctu.core.packets.PacketClientSecretKey;
import ctu.core.packets.PacketPing;
import ctu.core.packets.PacketServerPublicKey;
import ctu.core.threads.CTURunnable;

public abstract class CTU extends CTURunnable {
	private Config config;

	private ArrayList<Class<?>> clazzes = new ArrayList<>();

	private ExecutorService executorService = Executors.newCachedThreadPool();

	private ConcurrentLinkedQueue<Listener> listeners = new ConcurrentLinkedQueue<>();
	private ConcurrentLinkedQueue<CTURunnable> threads = new ConcurrentLinkedQueue<>();

	private Socket socket;

	public void execute(CTURunnable ctuRunnable) {
		threads.add(ctuRunnable);

		if (!executorService.isShutdown()) {
			executorService.execute(ctuRunnable);
		}
	}
	
	public void execute(Runnable runnable) {
		if (!executorService.isShutdown()) {
			executorService.execute(runnable);
		}
	}

	public void executorStop() {
		threads.forEach(ctuRunnable -> {
			if (ctuRunnable.isRunning()) {
				ctuRunnable.stop(threads);
			}
		});

		executorService.shutdown();

		try {
			if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
				if (!executorService.isShutdown()) {
					executorService.shutdownNow();
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public ArrayList<Class<?>> getClazzes() {
		return clazzes;
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	public void register(Class<?> clazz) {
		clazzes.add(clazz);

		clazzes.sort(new Comparator<Class<?>>() {
			@Override
			public int compare(Class<?> o1, Class<?> o2) {
				return o1.getSimpleName().compareTo(o2.getSimpleName());
			}
		});
	}

	public CTU() {
		register(PacketClientSecretKey.class);
		register(PacketPing.class);
		register(PacketServerPublicKey.class);
	}

	public void addListener(final Listener listener) {
		listeners.add(listener);
	}

	public ConcurrentLinkedQueue<Listener> getListeners() {
		return listeners;
	}

	public Socket getSocket() {
		return socket;
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	public Config getConfig() {
		return config;
	}

	public void setConfig(Config config) {
		this.config = config;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}

	public void start() {
		this.execute(this);
	}
}
