package examples;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.listeners.Listener;
import ctu.core.packets.PacketPing;
import ctu.core.server.Server;

/**
 * @author Fentus
 * 
 *         This is an example package containing a class called "ServerLauncher". The class launches a server that
 *         listens on port number 9091 for incoming connections from clients. The server uses a thread pool to execute
 *         tasks in a multithreaded environment. It registers a "PacketPing" class to send packets to the clients and
 *         adds a listener that implements the "Listener" interface to handle channel events. The "ExecutorService"
 *         submits the server to the thread pool for execution.
 */
public class ServerLauncher {
	public static void main(String[] args) {
		// Creating a new server that listens on port number 9091 for incoming connections from clients.
		Server server = new Server(9091, 10);

		// Registering the "PacketPing" class allows the server to send and receive this packet.
		server.register(PacketPing.class);

		// Adding a listener to the server to handle connection events.
		server.addListener(new Listener() {
			// Implementing the "channelActive" method to handle a channel active event. These events are only triggered
			// when a client connects.
			@Override
			public void channelActive(Connection connection) {

			}

			// Implementing the "channelRead" method to handle a channel read event. These events are only triggered
			// when a client sends a packet.
			@Override
			public void channelRead(Connection connection, Packet packet) {

			}

			// Implementing the "channelInactive" method to handle a channel inactive event. These events are only
			// triggered when a client disconnects correctly.
			@Override
			public void channelInactive(Connection connection) {

			}

			// Implementing the "channelExceptionCaught" method to handle a channel exception event. These events are
			// only triggered when a client disconnects incorrectly.
			@Override
			public void channelExceptionCaught(Connection connection) {

			}
		});

		server.start();
	}
}
