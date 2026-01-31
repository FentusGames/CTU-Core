package ctu.core.server.bridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import ctu.core.abstracts.Packet;
import ctu.core.logger.Log;
import ctu.core.server.config.RemoteServerConfig;
import ctu.core.server.config.ServerConfig;

public class ServerBridge<T> {
	private final ServerConfig config;
	private final Map<String, BridgeConnection<T>> connections = new ConcurrentHashMap<>();
	private final List<BridgeListener<T>> globalListeners = new CopyOnWriteArrayList<>();
	private final HashMap<Integer, Class<?>> packetClasses;
	private final Supplier<T> connectionObjectSupplier;
	private final int timeout;

	public ServerBridge(ServerConfig config, HashMap<Integer, Class<?>> packetClasses, Supplier<T> connectionObjectSupplier, int timeout) {
		this.config = config;
		this.packetClasses = packetClasses;
		this.connectionObjectSupplier = connectionObjectSupplier;
		this.timeout = timeout;
	}

	public void connectAll() {
		for (Map.Entry<String, RemoteServerConfig> entry : config.getServers().entrySet()) {
			connect(entry.getKey());
		}
	}

	public void connect(String serverId) {
		if (connections.containsKey(serverId)) {
			Log.debug("ServerBridge: Already connected/connecting to " + serverId);
			return;
		}

		RemoteServerConfig remoteConfig = config.getServer(serverId);
		if (remoteConfig == null) {
			Log.debug("ServerBridge: Unknown server ID: " + serverId);
			return;
		}

		BridgeConnection<T> connection = new BridgeConnection<>(config.getServerId(), serverId, remoteConfig, packetClasses, connectionObjectSupplier.get(), timeout);

		for (BridgeListener<T> listener : globalListeners) {
			connection.addListener(listener);
		}

		connections.put(serverId, connection);
		connection.connect();
	}

	public void disconnect(String serverId) {
		BridgeConnection<T> connection = connections.remove(serverId);
		if (connection != null) {
			connection.shutdown();
		}
	}

	public void disconnectAll() {
		for (BridgeConnection<T> connection : connections.values()) {
			connection.shutdown();
		}
		connections.clear();
	}

	public void sendToServer(String serverId, Packet packet) {
		BridgeConnection<T> connection = connections.get(serverId);
		if (connection != null) {
			connection.sendPacket(packet);
		} else {
			Log.debug("ServerBridge: No connection to server " + serverId);
		}
	}

	public void broadcast(Packet packet) {
		for (BridgeConnection<T> connection : connections.values()) {
			connection.sendPacket(packet);
		}
	}

	public BridgeConnection<T> getConnection(String serverId) {
		return connections.get(serverId);
	}

	public boolean isConnected(String serverId) {
		BridgeConnection<T> connection = connections.get(serverId);
		return connection != null && connection.isConnected();
	}

	public String findServerForSystem(long systemId) {
		for (Map.Entry<String, RemoteServerConfig> entry : config.getServers().entrySet()) {
			if (entry.getValue().ownsSystem(systemId)) {
				return entry.getKey();
			}
		}
		return null;
	}

	public void addListener(BridgeListener<T> listener) {
		globalListeners.add(listener);
		for (BridgeConnection<T> connection : connections.values()) {
			connection.addListener(listener);
		}
	}

	public void removeListener(BridgeListener<T> listener) {
		globalListeners.remove(listener);
		for (BridgeConnection<T> connection : connections.values()) {
			connection.removeListener(listener);
		}
	}

	public ServerConfig getConfig() {
		return config;
	}

	public int getConnectedCount() {
		int count = 0;
		for (BridgeConnection<T> connection : connections.values()) {
			if (connection.isConnected()) {
				count++;
			}
		}
		return count;
	}

	public int getTotalCount() {
		return connections.size();
	}
}
