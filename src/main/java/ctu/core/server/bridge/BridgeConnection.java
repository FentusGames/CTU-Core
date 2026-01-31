package ctu.core.server.bridge;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.callbacks.CallbackConnect;
import ctu.core.client.Client;
import ctu.core.interfaces.Listener;
import ctu.core.logger.Log;
import ctu.core.server.config.RemoteServerConfig;

public class BridgeConnection<T> implements Listener<T> {
	private final String remoteServerId;
	private final RemoteServerConfig remoteConfig;
	private final HashMap<Integer, Class<?>> packetClasses;
	private final List<BridgeListener<T>> listeners = new CopyOnWriteArrayList<>();
	private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

	private Client<T> client;
	private volatile boolean connected = false;
	private volatile boolean shouldReconnect = true;
	private ScheduledFuture<?> reconnectFuture;

	private final T connectionObject;
	private final int timeout;
	private final String localServerId;

	public BridgeConnection(String localServerId, String remoteServerId, RemoteServerConfig remoteConfig, HashMap<Integer, Class<?>> packetClasses, T connectionObject, int timeout) {
		this.localServerId = localServerId;
		this.remoteServerId = remoteServerId;
		this.remoteConfig = remoteConfig;
		this.packetClasses = packetClasses;
		this.connectionObject = connectionObject;
		this.timeout = timeout;
	}

	public void connect() {
		if (connected || client != null) {
			return;
		}

		Log.debug("BridgeConnection: Connecting to " + remoteServerId + " at " + remoteConfig.getHost() + ":" + remoteConfig.getPort());

		client = new Client<>(remoteConfig.getHost(), remoteConfig.getPort(), timeout, connectionObject);
		client.setPingName(localServerId + " -> " + remoteServerId);

		for (var entry : packetClasses.entrySet()) {
			client.register(entry.getValue());
		}

		client.start(new CallbackConnect() {
			@Override
			public void execute(boolean success) {
				if (success) {
					// Add listener first - channelActive will be called after TLS handshake
					client.addListener(BridgeConnection.this);
					Log.debug("BridgeConnection: Connected to " + remoteServerId);
					// Don't notify yet - wait for channelActive after TLS completes
				} else {
					Log.debug("BridgeConnection: Failed to connect to " + remoteServerId);
					connected = false;
					if (client != null) {
						client.close();
						client = null;
					}
					scheduleReconnect();
				}
			}
		});
	}

	public void disconnect() {
		shouldReconnect = false;

		if (reconnectFuture != null) {
			reconnectFuture.cancel(false);
			reconnectFuture = null;
		}

		if (client != null) {
			client.close();
			client = null;
		}

		connected = false;
	}

	public void sendPacket(Packet packet) {
		if (client != null && connected) {
			client.sendTCP(packet);
		} else {
			Log.debug("BridgeConnection: Cannot send packet to " + remoteServerId + " - not connected");
		}
	}

	public void addListener(BridgeListener<T> listener) {
		listeners.add(listener);
	}

	public void removeListener(BridgeListener<T> listener) {
		listeners.remove(listener);
	}

	public boolean isConnected() {
		return connected && client != null && client.isConnected();
	}

	public String getRemoteServerId() {
		return remoteServerId;
	}

	public RemoteServerConfig getRemoteConfig() {
		return remoteConfig;
	}

	private void scheduleReconnect() {
		if (!shouldReconnect) {
			return;
		}

		if (reconnectFuture != null && !reconnectFuture.isDone()) {
			return;
		}

		reconnectFuture = reconnectScheduler.schedule(() -> {
			if (shouldReconnect && !connected) {
				Log.debug("BridgeConnection: Attempting reconnect to " + remoteServerId);
				if (client != null) {
					client.close();
				}
				client = null;
				connect();
			}
		}, 5, TimeUnit.SECONDS);
	}

	private void notifyConnected() {
		for (BridgeListener<T> listener : listeners) {
			try {
				listener.onServerConnected(remoteServerId);
			} catch (Exception e) {
				Log.debug("BridgeConnection: Listener error on connect: " + e.getMessage());
			}
		}
	}

	private void notifyDisconnected() {
		for (BridgeListener<T> listener : listeners) {
			try {
				listener.onServerDisconnected(remoteServerId);
			} catch (Exception e) {
				Log.debug("BridgeConnection: Listener error on disconnect: " + e.getMessage());
			}
		}
	}

	private void notifyPacketReceived(Packet packet) {
		for (BridgeListener<T> listener : listeners) {
			try {
				listener.onPacketReceived(remoteServerId, packet);
			} catch (Exception e) {
				Log.debug("BridgeConnection: Listener error on packet: " + e.getMessage());
			}
		}
	}

	@Override
	public void channelActive(Connection<T> connection) {
		// Called after TLS handshake completes - now safe to send packets
		connected = true;
		Log.debug("BridgeConnection: Channel active (TLS ready) for " + remoteServerId);
		notifyConnected();
	}

	@Override
	public void channelInactive(Connection<T> connection) {
		Log.debug("BridgeConnection: Disconnected from " + remoteServerId);
		connected = false;
		if (client != null) {
			client.close();
			client = null;
		}
		notifyDisconnected();
		scheduleReconnect();
	}

	@Override
	public void channelExceptionCaught(Connection<T> connection) {
		Log.debug("BridgeConnection: Exception with " + remoteServerId);
		connected = false;
		if (client != null) {
			client.close();
			client = null;
		}
		notifyDisconnected();
		scheduleReconnect();
	}

	@Override
	public void channelRead(Connection<T> connection, Packet packet) {
		Log.trace(String.format("[%s <- %s] Received %s", localServerId, remoteServerId, packet.getClass().getSimpleName()));
		notifyPacketReceived(packet);
	}

	public void shutdown() {
		disconnect();
		reconnectScheduler.shutdownNow();
	}
}
