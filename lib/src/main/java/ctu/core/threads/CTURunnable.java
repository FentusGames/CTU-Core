package ctu.core.threads;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class CTURunnable implements Runnable {
	private Thread worker;
	private final AtomicBoolean running = new AtomicBoolean(false);

	private DataInputStream dataInputStream;
	private DataOutputStream dataOutputStream;

	private ServerSocket serverSocket;

	public void start() {
		worker = new Thread(this);
		worker.start();
	}

	public void stop(ConcurrentLinkedQueue<CTURunnable> threads) {
		try {
			if (serverSocket != null) {
				serverSocket.close();
			}
		} catch (IOException e) {
		}

		try {
			if (dataInputStream != null) {
				dataInputStream.close();
			}

			if (dataOutputStream != null) {
				dataOutputStream.close();
			}
		} catch (Exception e) {
		}

		setRunning(false);
		threads.remove(this);
	}

	public void run() {
		setRunning(true);

		exec();
	}

	public abstract void exec();

	public void setDataInputStream(DataInputStream dataInputStream) {
		this.dataInputStream = dataInputStream;
	}

	public void setDataOutputStream(DataOutputStream dataOutputStream) {
		this.dataOutputStream = dataOutputStream;
	}

	public void setServerSocket(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}

	public boolean isRunning() {
		return running.get();
	}

	public void setRunning(boolean running) {
		this.running.set(running);
	}
}
