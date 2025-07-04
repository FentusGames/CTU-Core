package ctu.core.server;

import java.util.Map;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.logger.Log;
import ctu.core.packets.PacketPing;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author     Fentus
 * 
 *             The ServerConnectionHandler class represents a connection handler for a server that communicates with clients over SSL/TLS for secure communication. It extends the Connection class and implements methods for handling active and inactive channels, exceptions, and reading from channels.\
 * @param  <T>
 */
public class ServerConnectionHandler<T> extends Connection<T> {
	private Server<T> server;

	public ServerConnectionHandler(Server<T> server) {
		this.server = server;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);

		long id = server.getNextConnectionId();

		this.setConnectionID(id);

		server.getConnectionMap().put(id, this);

		server.getListeners().forEach(listener -> listener.channelActive(this));

		Log.debug("New connection established with id: " + id);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		long id = getConnectionId(ctx.channel());

		boolean removed = server.getConnectionMap().values().removeIf(channel -> channel == ctx.channel());

		if (removed) {
			ctx.close(); // Ensure the channel is closed
			Log.debug("Connection closed and removed with id: " + id);
		}

		server.getListeners().forEach(listener -> listener.channelInactive(this));

		if (id != -1) {
			Log.debug("Connection closed with id: " + id);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		long id = getConnectionId(ctx.channel());

		server.getConnectionMap().values().removeIf(channel -> channel == ctx.channel());

		server.getListeners().forEach(listener -> listener.channelExceptionCaught(this));

		if (id != -1) {
			Log.debug("Connection closed with id: " + id);
		}
	}

	private synchronized long getConnectionId(Channel channel) {
		for (Map.Entry<Long, ServerConnectionHandler<T>> entry : server.getConnectionMap().entrySet()) {
			if (entry.getValue() == channel) {
				return entry.getKey();
			}
		}

		return -1;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
		int size = byteBuf.readableBytes();

		byte[] bytes = new byte[size];
		byteBuf.readBytes(bytes);

		// Convert bytes to packet
		Packet packet = bytesToPacket(bytes);

		// Send the ping packet right back.
		if (packet instanceof PacketPing) {
			sendTCP(packet);
		}

		// Do something with the packet
		if (packet != null) {
			server.getListeners().forEach(listener -> listener.channelRead(this, packet));
		}

		byteBuf.release();

		Log.debug("Received message from client. Bytes: " + size);
	}
}