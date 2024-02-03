package examples;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.client.Client;
import ctu.core.interfaces.Listener;
import ctu.core.packets.PacketPing;

/**
 * @author Fentus
 * 
 *         This is an example package that contains a class called "ClientLauncher". The class launches a client that
 *         connects to a server at localhost and port number 9091. The client uses a thread pool to execute tasks in a
 *         multi-threaded environment. Additionally, the client registers a "PacketPing" class to receive and send
 *         packets from the server. Furthermore, the client adds a listener that implements the "Listener" interface to
 *         handle channel events. Finally, the "ExecutorService" submits the client to the thread pool for execution.
 */
public class ClientLauncher {
	// Used for storing and retrieving custom data in the connection object.
	public class CustomConnection {

	}

	public static void main(String[] args) {
		// Creating a new client that connects to the server at an address and port 9091.
		Client<CustomConnection> client = new Client<CustomConnection>("localhost", 9091, 10);

		// Registering the "PacketPing" class allows the client to send and receive this packet.
		client.register(PacketPing.class);

		// Adding a listener to the client to handle connection events.
		client.addListener(new Listener<CustomConnection>() {
			// Implementing the "channelActive" method to handle a channel active event. These events are only triggered
			// when a client connects.
			@Override
			public void channelActive(Connection<CustomConnection> connection) {

			}

			// Implementing the "channelRead" method to handle a channel read event. These events are only triggered
			// when a client sends a packet.
			@Override
			public void channelRead(Connection<CustomConnection> connection, Packet packet) {

			}

			// Implementing the "channelInactive" method to handle a channel inactive event. These events are only
			// triggered when a client disconnects correctly.
			@Override
			public void channelInactive(Connection<CustomConnection> connection) {

			}

			// Implementing the "channelExceptionCaught" method to handle a channel exception event. These events are
			// only triggered when a client disconnects incorrectly.
			@Override
			public void channelExceptionCaught(Connection<CustomConnection> connection) {

			}
		});

		client.start();
	}
}
