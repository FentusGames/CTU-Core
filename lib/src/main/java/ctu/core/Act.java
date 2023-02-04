package ctu.core;

import java.util.InputMismatchException;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Connection.Mode;
import ctu.core.abstracts.Packet;
import ctu.core.interfaces.Listener;
import ctu.core.threads.CTURunnable;

public class Act extends CTURunnable {
	private Connection connection;
	private byte[] bytes;

	public Act(Connection connection, byte[] bytes) {
		this.connection = connection;
		this.bytes = bytes;
	}

	@Override
	public void exec() {
		final int size = bytes.length;

		final Packet packet = connection.bytesToPacket(bytes);

		if (packet != null) {
			try {
				System.out.println((connection.getMode() == Mode.CLIENT) ? String.format("[RECEIVING] %s [%s] from server.", packet.getClass().getSimpleName(), size) : String.format("[RECEIVING] %s [%s] from client #%s. (SID: %s)", packet.getClass().getSimpleName(), size, connection.getCID(), connection.getSID()));

				for (final Listener listener : connection.getCtu().getListeners()) {
					listener.recieved(connection, packet);
				}
			} catch (final InputMismatchException e) {
			}
		}
	}
}
