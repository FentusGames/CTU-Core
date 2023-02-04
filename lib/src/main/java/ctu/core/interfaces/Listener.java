package ctu.core.interfaces;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;

public interface Listener {
	void postConnect(Connection connection);

	void connected(Connection connection);

	void recieved(Connection connection, Packet packet);

	void disconnected(Connection connection);

	void reset(Connection connection);

	void timeout(Connection connection);
}
