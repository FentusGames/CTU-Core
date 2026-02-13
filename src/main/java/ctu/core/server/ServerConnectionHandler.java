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
 *             The ServerConnectionHandler class represents a connection handler for a server that communicates with clients over SSL/TLS for secure communication.
 *
 *             Listener dispatch is performed by Server via per-listener single-thread executors.
 *
 * @param  <T>
 */
public class ServerConnectionHandler<T> extends Connection<T> {
	private final Server<T> server;

	/**
	 * Constructs a new ServerConnectionHandler with a given server and connectionObject.
	 *
	 * @param server           the server managing this handler
	 * @param connectionObject the associated connection object (never null)
	 */
	public ServerConnectionHandler(Server<T> server, T connectionObject) {
		super(connectionObject);
		this.server = server;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if (isInactive()) {
			return;
		}

		super.channelActive(ctx);

		long id = server.getNextConnectionId();

		this.setConnectionID(id);

		// Add to unassigned shard initially (will be moved to correct shard on login)
		server.addConnectionToShard(id, this, Server.UNASSIGNED_SHARD);

		// Dispatch to listeners on their own threads (one thread per listener).
		server.dispatchChannelActive(this);

		Log.debug("New connection established with id: " + id);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (isInactive()) {
			return;
		}

		super.channelInactive(ctx);

		setInactive(true);

		// Dispatch to listeners on their own threads (one thread per listener).
		// Note: Connection is NOT removed here - cleanup listener handles removal after grace period
		server.dispatchChannelInactive(this);

		Log.debug("Connection inactive (id: " + getConnectionID() + ")");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (isInactive()) {
			return;
		}

		setInactive(true);

		// Dispatch to listeners on their own threads (one thread per listener).
		// Note: Connection is NOT removed here - cleanup listener handles removal after grace period
		server.dispatchChannelExceptionCaught(this);

		Log.debug("Connection error (id: " + getConnectionID() + ")");

		ctx.close();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
		if (isInactive()) {
			return;
		}

		super.channelRead0(ctx, byteBuf);

		int size = byteBuf.readableBytes();

		// Track received bandwidth
		addBytesReceived(size);

		byte[] bytes = new byte[size];
		byteBuf.readBytes(bytes);

		// Convert bytes to packet
		Packet packet = bytesToPacket(bytes);

		// Send the ping packet right back (keep this immediate).
		if (packet instanceof PacketPing) {
			sendTCP(packet);
		}

		// Dispatch to listeners on their own threads (one thread per listener).
		if (packet != null) {
			server.dispatchChannelRead(this, packet);
		}

		// Guard against packet being null to avoid NPE in logs.
		String packetName = (packet == null) ? "null" : packet.getClass().getName();

		Log.trace("Received TCP packet: " + packetName + ", Size: " + size + " bytes. ");
	}
}
