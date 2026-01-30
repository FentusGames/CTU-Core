package examples;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.interfaces.Listener;
import ctu.core.logger.Log;
import ctu.core.packets.PacketPing;
import ctu.core.server.Server;
import ctu.core.server.config.ServerArgs;
import ctu.core.server.config.ServerConfig;
import ctu.core.server.config.ServerType;

/**
 * Example Server demonstrating CTU-Core features:
 * - Command-line argument parsing
 * - Server configuration
 * - Packet registration and handling
 * - Listener-based event handling with dedicated worker threads
 *
 * Usage:
 *   java -jar server.jar serverId=server-1 port=29902
 *
 * Arguments:
 *   serverId=<id>       Server identifier (default: server-1)
 *   port=<port>         Port to listen on (default: 29902)
 *   publicHost=<host>   Public hostname for client connections
 *   publicPort=<port>   Public port for client connections
 */
public class ServerLauncher {

	/**
	 * Custom connection object for storing per-connection state.
	 * Extend this class to add your own fields.
	 */
	public static class ConnectionData {
		private long oderId;
		private String identifier;

		public long getUserId() {
			return oderId;
		}

		public void setUserId(long userId) {
			this.oderId = userId;
		}

		public String getIdentifier() {
			return identifier;
		}

		public void setIdentifier(String identifier) {
			this.identifier = identifier;
		}
	}

	public static void main(String[] args) {
		// Parse command-line arguments
		ServerArgs serverArgs = ServerArgs.parse(args);

		// Build server configuration
		ServerConfig config = buildConfig(serverArgs);

		Log.debug("Starting Server: " + config.getServerId() + " on port " + config.getPort());

		// Create server with connection timeout (seconds) and connection object supplier
		Server<ConnectionData> server = new Server<>(config.getPort(), 10, ConnectionData::new);

		// Register packets - ORDER MUST BE IDENTICAL across all servers and clients
		server.register(PacketPing.class);
		// Register your custom packets here:
		// server.register(YourPacket.class);

		// Add listeners with dedicated worker threads
		// Each listener runs on its own thread for parallel processing
		server.addListener(new Listener<ConnectionData>() {
			@Override
			public void channelActive(Connection<ConnectionData> connection) {
				// Called when a client connects (after TLS handshake)
				Log.debug("Client connected: " + connection.getConnectionID());
			}

			@Override
			public void channelRead(Connection<ConnectionData> connection, Packet packet) {
				// Called when a packet is received from a client
				if (packet instanceof PacketPing) {
					// Echo ping back to client
					connection.sendTCP(packet);
				}
			}

			@Override
			public void channelInactive(Connection<ConnectionData> connection) {
				// Called when a client disconnects normally
				Log.debug("Client disconnected: " + connection.getConnectionID());
			}

			@Override
			public void channelExceptionCaught(Connection<ConnectionData> connection) {
				// Called when a client disconnects due to an error
				Log.debug("Client error: " + connection.getConnectionID());
			}
		}, "MainListener");

		// Start the server
		server.start();

		Log.debug("Server started on port " + config.getPort());

		// Keep main thread alive
		try {
			Thread.currentThread().join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static ServerConfig buildConfig(ServerArgs args) {
		ServerConfig config = new ServerConfig();

		// Set defaults
		config.setServerId("server-1");
		config.setServerType(ServerType.GAME);
		config.setHost("0.0.0.0");
		config.setPort(29902);
		config.setPublicHost("localhost");
		config.setPublicPort(29902);
		config.setTransferTokenSecret("change-me-in-production");
		config.setTransferTokenExpirySeconds(30);

		// Apply command-line args (override defaults)
		config.applyArgs(args);

		return config;
	}
}
