package examples;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.client.Client;
import ctu.core.listeners.Listener;
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
	public static void main(String[] args) {
		// Creating a new client that connects to the server at an address and port 9091.
		Client client = new Client("10.89.0.6", 9091, 10);

		// Registering the "PacketPing" class allows the client to send and receive this packet.
		client.register(PacketPing.class);

		// Adding a listener to the client to handle connection events.
		client.addListener(new Listener() {
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

		client.start();
		
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		client.close();
	}
}
