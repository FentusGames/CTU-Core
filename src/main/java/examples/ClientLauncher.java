package examples;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.callbacks.CallbackConnect;
import ctu.core.client.Client;
import ctu.core.interfaces.Listener;
import ctu.core.logger.Log;
import ctu.core.packets.PacketPing;

/**
 * Example Client demonstrating CTU-Core features:
 * - Connecting to a server with TLS
 * - Packet registration and handling
 * - Connection callbacks
 * - Listener-based event handling
 *
 * Usage:
 *   java -jar client.jar
 *
 * IMPORTANT: TLS Timing
 *   - CallbackConnect.execute(true) means TCP connected, but TLS handshake is still in progress
 *   - Wait for channelActive() before sending packets - this is when TLS is ready
 */
public class ClientLauncher {

	/**
	 * Custom connection object for storing client-side state.
	 */
	public static class ConnectionData {
		private long id;
		private String identifier;

		public long getId() {
			return id;
		}

		public void setId(long id) {
			this.id = id;
		}

		public String getIdentifier() {
			return identifier;
		}

		public void setIdentifier(String identifier) {
			this.identifier = identifier;
		}
	}

	public static void main(String[] args) {
		// Create client connecting to server
		// Parameters: host, port, timeout (seconds), connection object
		Client<ConnectionData> client = new Client<>("localhost", 29902, 10, new ConnectionData());

		// Register packets - ORDER MUST BE IDENTICAL to the server
		client.register(PacketPing.class);
		// Register your custom packets here (same order as server):
		// client.register(YourPacket.class);

		// Add listener for handling server events
		client.addListener(new Listener<ConnectionData>() {
			@Override
			public void channelActive(Connection<ConnectionData> connection) {
				// Called after TLS handshake completes - safe to send packets now
				Log.debug("Connected to server (TLS ready)");

				// Send a ping to test the connection
				PacketPing ping = new PacketPing();
				connection.sendTCP(ping);
			}

			@Override
			public void channelRead(Connection<ConnectionData> connection, Packet packet) {
				// Called when a packet is received from the server
				if (packet instanceof PacketPing) {
					Log.debug("Received ping response from server");
				}
			}

			@Override
			public void channelInactive(Connection<ConnectionData> connection) {
				// Called when disconnected from server normally
				Log.debug("Disconnected from server");
			}

			@Override
			public void channelExceptionCaught(Connection<ConnectionData> connection) {
				// Called when disconnected due to an error
				Log.debug("Connection error");
			}
		});

		// Connect with callback
		client.start(new CallbackConnect() {
			@Override
			public void execute(boolean success) {
				if (success) {
					Log.debug("TCP connection established, TLS handshake in progress...");
					// IMPORTANT: Don't send packets here - wait for channelActive()
				} else {
					Log.debug("Failed to connect to server");
				}
			}
		});

		// Keep main thread alive
		try {
			Thread.currentThread().join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
