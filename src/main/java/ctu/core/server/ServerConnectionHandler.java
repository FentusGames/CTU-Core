package ctu.core.server;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.logger.Log;
import ctu.core.packets.PacketPing;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author     Fentus
 * 
 *             The ServerConnectionHandler class represents a connection handler for a server that communicates with clients over SSL/TLS for secure communication. It extends the Connection class and implements methods for handling active and inactive channels, exceptions, and reading from channels.\
 * @param  <T>
 */
public class ServerConnectionHandler<T> extends Connection<T> {
	private Server<T> server;

	/**
	 * Constructs a new ServerConnectionHandler with a given server, userID, and connectionObject.
	 *
	 * @param server           the server managing this handler
	 * @param userID           the user ID (must be positive and non-zero)
	 * @param connectionObject the associated connection object (never null)
	 */
	public ServerConnectionHandler(Server<T> server, T connectionObject) {
		super(connectionObject);
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
		super.channelInactive(ctx);

		boolean removed = server.getConnectionMap().values().removeIf(channel -> channel.getCtx().equals(ctx));

		if (removed) {
			ctx.close();
		}

		server.getListeners().forEach(listener -> listener.channelInactive(this));

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		super.exceptionCaught(ctx, cause);

		server.getConnectionMap().values().removeIf(channel -> channel.getCtx().equals(ctx));
		server.getListeners().forEach(listener -> listener.channelExceptionCaught(this));
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
		super.channelRead0(ctx, byteBuf);

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

		Log.debug("Received TCP packet: " + packet.getClass().getName() + ", Size: " + size + " bytes. ");
	}
}