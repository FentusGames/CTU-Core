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
	/**
	 * Per-connection flood cap: a token bucket checked for every frame on the
	 * Netty thread, before the frame is inflated or fanned out to every
	 * listener. A client refills {@link #FLOOD_RATE_PER_SEC} packets a second
	 * and may save up {@link #FLOOD_BURST}; a frame with no token is dropped.
	 * A connection that has dropped more than {@link #FLOOD_MAX_DROPS} frames
	 * inside one {@link #FLOOD_WINDOW_NANOS} window is closed. A client at
	 * twice the rate is gone in about five seconds; one slightly over it is
	 * only trimmed. Real clients stay far below this: ListenerCommand, the
	 * busiest handler, takes 20 a second. Per connection, never per IP, so
	 * several clients on one address (multiboxing, CGNAT) each have their own.
	 */
	static final double FLOOD_RATE_PER_SEC = 100;
	static final double FLOOD_BURST = 500;
	static final long FLOOD_WINDOW_NANOS = 5_000_000_000L;
	static final int FLOOD_MAX_DROPS = 500;

	private final Server<T> server;

	// Flood bucket state. channelRead0 for one channel always runs on that
	// channel's event loop thread, so these need no synchronisation.
	private double floodTokens = FLOOD_BURST;
	private long floodRefilledAtNanos = System.nanoTime();
	private long floodWindowStartNanos = 0;
	private int floodDrops = 0;
	private boolean floodClosed = false;

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

		if (!admitFrame(System.nanoTime())) {
			return;
		}

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

		if (Log.isTraceEnabled()) {
			// Guard against packet being null to avoid NPE in logs.
			String packetName = (packet == null) ? "null" : packet.getClass().getName();

			Log.trace("Received TCP packet: " + packetName + ", Size: " + size + " bytes. ");
		}
	}

	/**
	 * Takes one token for a frame arriving at {@code nowNanos}. False drops the
	 * frame; a connection over {@link #FLOOD_MAX_DROPS} in one window is also
	 * closed. A connection the server exempts (its bridge peers) always passes.
	 */
	boolean admitFrame(long nowNanos) {
		long elapsed = nowNanos - floodRefilledAtNanos;
		if (elapsed > 0) {
			floodTokens = Math.min(FLOOD_BURST, floodTokens + elapsed * (FLOOD_RATE_PER_SEC / 1_000_000_000d));
			floodRefilledAtNanos = nowNanos;
		}

		if (floodTokens >= 1) {
			floodTokens -= 1;
			return true;
		}

		if (server.isFloodExempt(this)) {
			return true;
		}

		if (floodDrops == 0 || nowNanos - floodWindowStartNanos > FLOOD_WINDOW_NANOS) {
			floodWindowStartNanos = nowNanos;
			floodDrops = 0;
		}
		floodDrops++;

		if (floodDrops > FLOOD_MAX_DROPS && !floodClosed) {
			floodClosed = true;
			Log.warn("Flood cap: closing connection " + getConnectionID() + " (user " + getUserID() + ", " + describeRemote() + "): over " + FLOOD_MAX_DROPS + " packets dropped in " + (FLOOD_WINDOW_NANOS / 1_000_000_000L) + " s at " + (int) FLOOD_RATE_PER_SEC + "/s");
			if (getCtx() != null) {
				getCtx().close();
			}
		}
		return false;
	}

	private String describeRemote() {
		try {
			return String.valueOf(getCtx().channel().remoteAddress());
		} catch (Exception e) {
			return "unknown";
		}
	}
}
