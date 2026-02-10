package ctu.core.interfaces;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;

/**
 * 
 * The "Listener" class is an abstract class in the "ctu.core.listeners" package, which contains abstract methods for
 * handling various events related to network connections. This class is meant to be extended by other classes that want
 * to implement their own specific behavior for these events.
 * 
 * @author     Fentus
 * @param  <T>
 */
public interface Listener<T> {
	default void channelActive(Connection<T> connection) {}

	void channelRead(Connection<T> connection, Packet packet);

	void channelInactive(Connection<T> connection);

	default void channelExceptionCaught(Connection<T> connection) {}
}
