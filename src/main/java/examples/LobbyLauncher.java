package examples;

import java.util.HashMap;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.interfaces.Listener;
import ctu.core.logger.Log;
import ctu.core.packets.PacketPing;
import ctu.core.server.Server;
import ctu.core.server.bridge.BridgeListener;
import ctu.core.server.bridge.ServerBridge;
import ctu.core.server.config.RemoteServerConfig;
import ctu.core.server.config.ServerArgs;
import ctu.core.server.config.ServerConfig;
import ctu.core.server.config.ServerType;

/**
 * Example Lobby/Gateway Server demonstrating CTU-Core distributed architecture:
 * - Gateway server accepts client connections
 * - Routes clients to backend servers via ServerBridge
 * - Server-to-server communication over TLS
 * - Transfer tokens for secure client handoff between servers
 *
 * Architecture:
 *   Client -> Gateway (authenticate/route) -> Redirect -> Backend Server
 *
 * Usage:
 *   java -jar lobby.jar serverId=gateway-1 port=29901
 *
 * Start order:
 *   1. Start all backend servers first
 *   2. Start gateway server (connects to backend servers via bridge)
 */
public class LobbyLauncher {

	/**
	 * Custom connection object for gateway connections.
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
		// Parse command-line arguments
		ServerArgs serverArgs = ServerArgs.parse(args);

		// Build server configuration with backend server definitions
		ServerConfig config = buildConfig(serverArgs);

		Log.debug("Starting Gateway Server: " + config.getServerId());

		// Create gateway server
		Server<ConnectionData> server = new Server<>(config.getPort(), 10, ConnectionData::new);

		// Register packets - ORDER MUST BE IDENTICAL across all servers and clients
		server.register(PacketPing.class);
		// Register your custom packets here

		// Get registered packets for the bridge (must use same registration)
		HashMap<Integer, Class<?>> packetClasses = server.getRegisteredPackets();

		// Create ServerBridge for connecting to backend servers
		ServerBridge<ConnectionData> bridge = new ServerBridge<>(config, packetClasses, ConnectionData::new, 10);

		// Start the server
		server.start();

		// Add client listener
		server.addListener(new Listener<ConnectionData>() {
			@Override
			public void channelActive(Connection<ConnectionData> connection) {
				Log.debug("Client connected to gateway: " + connection.getConnectionID());
			}

			@Override
			public void channelRead(Connection<ConnectionData> connection, Packet packet) {
				// Handle client packets, authenticate, route to backend servers
			}

			@Override
			public void channelInactive(Connection<ConnectionData> connection) {
				Log.debug("Client disconnected from gateway: " + connection.getConnectionID());
			}

			@Override
			public void channelExceptionCaught(Connection<ConnectionData> connection) {
				Log.debug("Client error in gateway: " + connection.getConnectionID());
			}
		}, "GatewayListener");

		// Add bridge listener for backend server events
		bridge.addListener(new BridgeListener<ConnectionData>() {
			@Override
			public void onServerConnected(String serverId) {
				// Called when connected to a backend server (after TLS handshake)
				Log.debug("Connected to backend server: " + serverId);
			}

			@Override
			public void onServerDisconnected(String serverId) {
				// Called when disconnected from a backend server
				Log.debug("Disconnected from backend server: " + serverId);
			}

			@Override
			public void onPacketReceived(String serverId, Packet packet) {
				// Handle packets received from backend servers
			}
		});

		// Connect to all configured backend servers
		Log.debug("Connecting to backend servers...");
		bridge.connectAll();

		Log.debug("Gateway server started on port " + config.getPort());

		// Shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			Log.debug("Gateway shutdown initiated...");
			bridge.disconnectAll();
		}, "GatewayShutdownHook"));

		// Keep main thread alive
		try {
			Thread.currentThread().join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static ServerConfig buildConfig(ServerArgs args) {
		ServerConfig config = new ServerConfig();
		config.setServerId(args.getServerId() != null ? args.getServerId() : "gateway-1");
		config.setServerType(ServerType.LOBBY);
		config.setPort(args.getPort() != null ? args.getPort() : 29901);
		config.setTransferTokenSecret("change-me-in-production");

		// Configure backend servers
		// In production, load from config file or environment variables

		// Backend server 1
		RemoteServerConfig backend1 = new RemoteServerConfig();
		backend1.setHost("127.0.0.1");
		backend1.setPort(29902);
		backend1.setPublicHost("127.0.0.1");
		backend1.setPublicPort(29902);
		backend1.setType(ServerType.GAME);
		config.addServer("backend-1", backend1);

		// Backend server 2
		RemoteServerConfig backend2 = new RemoteServerConfig();
		backend2.setHost("127.0.0.1");
		backend2.setPort(29903);
		backend2.setPublicHost("127.0.0.1");
		backend2.setPublicPort(29903);
		backend2.setType(ServerType.GAME);
		config.addServer("backend-2", backend2);

		// Apply command-line overrides
		config.applyArgs(args);

		return config;
	}
}
