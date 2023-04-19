package ctu.core.client;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.listeners.Listener;
import ctu.core.logger.Log;
import ctu.core.packets.PacketPing;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author Fentus
 * 
 *         The ClientConnectionHandler class represents a handler for a client connection to a server via SSL/TLS for
 *         secure communication. It extends the abstract class Connection and overrides methods for handling channel
 *         events like channelActive, channelInactive, channelRead0 and exceptionCaught. It also provides methods to add
 *         or remove a Listener. When a client is connected to the server, the channelActive method is called and
 *         notifies all registered listeners of the channel activation. When the server closes the connection, the
 *         channelInactive method is called and notifies all registered listeners of the channel deactivation. The
 *         channelRead0 method reads and processes the received bytes, converts them to a Packet and sends the packet to
 *         all registered listeners. The exceptionCaught method handles exceptions caused by the channel and notifies
 *         all registered listeners of the exception.
 */
public class ClientConnectionHandler<T> extends Connection<T> {
	private ArrayList<Listener<T>> listeners = new ArrayList<>();
	private Client<T> client;

	public ClientConnectionHandler(Client<T> client) {
		this.client = client;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);

		listeners.forEach(listener -> listener.channelActive(this));

		Log.debug("Client connected to server");
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		listeners.forEach(listener -> listener.channelInactive(this));

		Log.debug("Connection closed by server");
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
		int size = byteBuf.readableBytes();

		byte[] bytes = new byte[size];
		byteBuf.readBytes(bytes);

		// Convert bytes to packet
		Packet packet = bytesToPacket(bytes);

		// Set ping time.
		if (packet instanceof PacketPing) {
			PacketPing packetPing = (PacketPing) packet;

			client.setPing(Duration.between(packetPing.time, Instant.now()).toNanos());
		} else {
			// Do something with the packet
			if (packet != null) {
				listeners.forEach(listener -> listener.channelRead(this, packet));
			}
		}

		byteBuf.release();

		Log.debug("Received message from server. Bytes: " + size);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		listeners.forEach(listener -> listener.channelExceptionCaught(this));

		Log.debug("Connection closed by server: " + cause.getMessage());
	}

	public void addListener(Listener<T> listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener<T> listener) {
		listeners.remove(listener);
	}
}
