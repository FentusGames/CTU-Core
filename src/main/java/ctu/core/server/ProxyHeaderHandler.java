package ctu.core.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import ctu.core.logger.Log;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;

/**
 * Reads the PROXY protocol header (v1 or v2) that a trusted TCP proxy such as
 * HAProxy writes ahead of each connection it forwards, so the server sees the
 * player's address instead of the proxy's. {@link Server} puts it, behind a
 * {@code HAProxyMessageDecoder}, only on connections whose socket peer is one of
 * its trusted proxies. Anyone else's bytes go straight to TLS, where a forged
 * header is only a failed handshake.
 *
 * channelActive is held back until the header has arrived, so the connection
 * handler and the listeners first meet the connection with the player's address
 * already set, and one over the per-IP cap never reaches them at all, the same as
 * a direct connection refused in Server.initChannel. A trusted connection that
 * sends no header is closed by the decoder or the read timeout.
 */
public final class ProxyHeaderHandler extends ChannelInboundHandlerAdapter {

	/** The player's address from the header; unset on a direct connection. */
	static final AttributeKey<InetSocketAddress> CLIENT_ADDRESS = AttributeKey.valueOf("ctu.proxy.clientAddress");

	private final Server<?> server;

	ProxyHeaderHandler(Server<?> server) {
		this.server = server;
	}

	/**
	 * The address of the other end of {@code channel}: the player's address from
	 * the PROXY header when a trusted proxy forwarded the connection, else the
	 * socket's own peer.
	 */
	public static InetSocketAddress clientAddress(Channel channel) {
		InetSocketAddress client = channel.attr(CLIENT_ADDRESS).get();
		if (client != null) {
			return client;
		}
		return channel.remoteAddress() instanceof InetSocketAddress peer ? peer : null;
	}

	/**
	 * The source address a PROXY header carries, or null when it carries none: a
	 * LOCAL command (the proxy's own health check) or an unknown or UNIX protocol.
	 * Parses the literal only; no DNS lookup.
	 */
	static InetSocketAddress sourceOf(HAProxyMessage header) {
		if (header.command() != HAProxyCommand.PROXY || header.sourceAddress() == null) {
			return null;
		}
		InetAddress address = NetUtil.createInetAddressFromIpAddressString(header.sourceAddress());
		return address == null ? null : new InetSocketAddress(address, header.sourcePort());
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		// Held until the header arrives (channelRead).
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (!(msg instanceof HAProxyMessage header)) {
			ctx.fireChannelRead(msg);
			return;
		}

		InetSocketAddress source;
		try {
			source = sourceOf(header);
		} finally {
			header.release();
		}

		Channel channel = ctx.channel();
		if (source != null) {
			channel.attr(CLIENT_ADDRESS).set(source);
		}

		InetSocketAddress client = clientAddress(channel);
		String ip = (client != null) ? client.getAddress().getHostAddress() : "unknown";
		if (!server.admitPerIp(channel, ip)) {
			Log.debug("Rejecting connection from " + ip + " via proxy " + channel.remoteAddress() + ": per-IP connection cap reached.");
			ctx.close();
			return;
		}

		ctx.fireChannelActive();
		ctx.pipeline().remove(this);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		// Still here means no header yet: the connection never went active, so the
		// connection handler and the listeners must not hear of it.
		Log.debug("PROXY header refused from " + ctx.channel().remoteAddress() + ": " + cause.getMessage());
		ctx.close();
	}
}
