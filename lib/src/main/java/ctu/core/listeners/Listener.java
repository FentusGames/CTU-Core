package ctu.core.listeners;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;

/**
 * 
 * The "Listener" class is an abstract class in the "ctu.core.listeners" package, which contains abstract methods for
 * handling various events related to network connections. This class is meant to be extended by other classes that want
 * to implement their own specific behavior for these events.
 * 
 * @author Fentus
 */
public abstract class Listener {
	// This method is called when a connection is established and becomes active. It takes a "Connection" object as a
	// parameter, which represents the connection that became active.
	public abstract void channelActive(Connection connection);

	// This method is called when a packet is received on the connection. It takes a "Connection" object and a "Packet"
	// object as parameters, which represent the connection and the received packet, respectively.
	public abstract void channelRead(Connection connection, Packet packet);

	// This method is called when a connection is closed and becomes inactive. It takes a "Connection" object as a
	// parameter, which represents the connection that became inactive.
	public abstract void channelInactive(Connection connection);

	// This method is called when an exception is thrown during any of the above methods. It takes a "Connection" object
	// as a parameter, which represents the connection on which the exception occurred.
	public abstract void channelExceptionCaught(Connection connection);
}
