package ctu.core.server.bridge;

import ctu.core.abstracts.Packet;

public interface BridgeListener<T> {
	void onServerConnected(String serverId);

	void onServerDisconnected(String serverId);

	void onPacketReceived(String serverId, Packet packet);
}
