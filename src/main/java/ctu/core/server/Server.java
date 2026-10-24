package ctu.core.server;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.net.ssl.SSLException;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.interfaces.Listener;
import ctu.core.logger.Log;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.NetUtil;

/**
 *
 * @author     Fentus
 *
 *             The Server class represents a server that listens for incoming client connections and handles them.
 *
 *             Change: - addListener(listener, name) registers a listener with a friendly name for logging/debug visibility. - Listener callbacks are dispatched using a dedicated Thread per listener.
 *
 *             Notes: - Each listener has a single worker thread that processes events sequentially (preserves order for that listener). - Slow listeners will not block Netty threads, but can build up their own queue. - Listener implementations must still be thread-safe with respect to shared state.
 *
 * @param  <T>
 */
public class Server<T> implements Runnable {

	/** Special shard ID for connections not yet assigned */
	public static final int UNASSIGNED_SHARD = -1;

	/** Max concurrent connections accepted from a single source IP, checked pre-TLS-handshake. */
	private static final int MAX_CONNECTIONS_PER_IP = 10;

	/** Max total concurrent connections accepted server-wide, checked pre-TLS-handshake. */
	private static final int MAX_TOTAL_CONNECTIONS = 20_000;

	/** Source IP -> concurrent accepted connection count, for the caps above. */
	private final ConcurrentHashMap<String, AtomicInteger> connectionsPerIp = new ConcurrentHashMap<>();

	private final AtomicInteger totalConnections = new AtomicInteger();

	/** Shard ID -> connections in that shard */
	private final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, ServerConnectionHandler<T>>> shardedConnections = new ConcurrentHashMap<>();

	/** Connection ID -> Shard ID (reverse lookup for fast moves) */
	private final ConcurrentHashMap<Long, Integer> connectionShardMap = new ConcurrentHashMap<>();

	/** System ID -> Shard ID mapping (configured at startup) */
	private volatile Map<Long, Integer> systemToShardMap = Collections.emptyMap();

	private final EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
	private final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

	private final int port;
	private final int timeout;
	private final Supplier<T> connectionObjectSupplier;
	private final int marshalBufferSize;

	private SslContext sslCtx;

	private int connectionId;

	private final HashMap<Integer, Class<?>> clazzes = new HashMap<>();
	private int key = 0;

	/**
	 * Named listener registrations.
	 *
	 * CopyOnWriteArrayList: - Safe iteration without external locks. - Great when dispatch is frequent and listeners are rarely added/removed.
	 */
	private final CopyOnWriteArrayList<NamedListener<T>> listeners = new CopyOnWriteArrayList<>();

	/**
	 * Decides, on the Netty thread and before the fan-out, whether an inbound
	 * packet reaches the listeners at all. Null admits everything. The game sets
	 * a login gate here, so a connection that has not logged in reaches only the
	 * handful of listeners that log it in, instead of every listener there is.
	 */
	private volatile BiPredicate<Connection<T>, Packet> packetGate;

	/**
	 * Connections the per-connection flood cap in {@link ServerConnectionHandler}
	 * never throttles, such as another server's bridge link, which carries every
	 * player's transfer on one connection. Null exempts none.
	 */
	private volatile Predicate<Connection<T>> floodExemption;

	/**
	 * Socket peers whose connections open with a PROXY protocol header naming the
	 * player's address; see {@link #setTrustedProxies}. Empty trusts none.
	 */
	private volatile List<IpSubnetFilterRule> trustedProxies = List.of();

	/**
	 * Constructs a new Server object with the given port number. It also initializes the SSL context with the specified SSL provider, protocols, and the server's certificate and private key for secure communication.
	 *
	 * @param port                     the port to bind the server
	 * @param timeout                  timeout for read/write handlers in seconds
	 * @param connectionObjectSupplier supplier to create new non-null connection objects for each connection
	 */
	public Server(int port, int timeout, Supplier<T> connectionObjectSupplier) {
		this(port, timeout, connectionObjectSupplier, ctu.core.abstracts.Connection.DEFAULT_MARSHAL_BUFFER_SIZE);
	}

	/**
	 * Constructs a new Server with an explicit marshal buffer size for outbound packets.
	 * Must be ≥ the largest expected marshalled packet size (i.e. ≥ the largest packet type's
	 * colferSizeMax). Set this when sending bundled batches (e.g. PacketPositionBatch with
	 * a raised colferSizeMax of 16384).
	 */
	public Server(int port, int timeout, Supplier<T> connectionObjectSupplier, int marshalBufferSize) {
		this.port = port;
		this.timeout = timeout;
		this.connectionObjectSupplier = connectionObjectSupplier;
		this.marshalBufferSize = marshalBufferSize;

		try {
			// @formatter:off
			this.sslCtx = SslContextBuilder
				.forServer(new File("server.crt"), new File("server.key"))
				.sslProvider(SslProvider.JDK)
				.protocols("TLSv1.3")
				.build();
			// @formatter:on
		} catch (SSLException e) {
			// Make SSL failures loud/early.
			throw new RuntimeException(e);
		}
	}

	private Server<T> getServer() {
		return this;
	}

	/**
	 * Starts the server by running the Netty bootstrap on a dedicated thread.
	 *
	 * If you want a non-daemon thread, setDaemon(false).
	 */
	public void start() {
		Thread t = new Thread(this);
		t.setName("NettyServer-" + port);
		t.setDaemon(true);
		t.start();
	}

	public long getNextConnectionId() {
		return connectionId++;
	}

	public void register(Class<?> clazz) {
		clazzes.put(key++, clazz);
	}

	public HashMap<Integer, Class<?>> getRegisteredPackets() {
		return clazzes;
	}

	public void broadcastTCP(Packet packet) {
		for (ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard : shardedConnections.values()) {
			shard.forEach((id, handler) -> handler.sendTCP(packet));
		}
	}

	public void broadcastTCP(Packet packet, Predicate<Connection<T>> condition) {
		for (ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard : shardedConnections.values()) {
			shard.forEach((id, handler) -> {
				if (condition.test(handler)) {
					handler.sendTCP(packet);
				}
			});
		}
	}

	/*
	 * ========================= Sharding API =========================
	 */

	/**
	 * Configure the system-to-shard mapping. Must be called before connections arrive.
	 *
	 * @param systemsByThread Map from shard/thread index (0 to N-1) to list of system IDs
	 */
	public void configureShards(Map<Integer, List<Long>> systemsByThread) {
		Map<Long, Integer> mapping = new HashMap<>();

		for (Map.Entry<Integer, List<Long>> entry : systemsByThread.entrySet()) {
			int shardId = entry.getKey();
			for (Long systemId : entry.getValue()) {
				mapping.put(systemId, shardId);
			}
			// Pre-create shard maps
			shardedConnections.putIfAbsent(shardId, new ConcurrentHashMap<>());
		}

		// Create unassigned shard
		shardedConnections.putIfAbsent(UNASSIGNED_SHARD, new ConcurrentHashMap<>());

		this.systemToShardMap = Collections.unmodifiableMap(mapping);

		Log.debug("Configured " + systemsByThread.size() + " shards with " + mapping.size() + " system mappings");
	}

	/**
	 * Get the shard ID for a given system ID.
	 * Returns UNASSIGNED_SHARD if the system is not mapped.
	 */
	public int getShardForSystem(long systemId) {
		return systemToShardMap.getOrDefault(systemId, UNASSIGNED_SHARD);
	}

	/**
	 * Add a new connection to a specific shard.
	 * Called during channelActive().
	 */
	void addConnectionToShard(long connectionId, ServerConnectionHandler<T> handler, int shardId) {
		ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard = shardedConnections.computeIfAbsent(shardId, key -> new ConcurrentHashMap<>());
		shard.put(connectionId, handler);
		connectionShardMap.put(connectionId, shardId);
	}

	/**
	 * Assign a connection to a specific shard based on system ID.
	 * Thread-safe: uses copy-then-remove pattern for safe concurrent iteration.
	 *
	 * @param connectionId The connection ID
	 * @param systemId The system ID to assign to (determines shard)
	 * @return true if moved successfully, false if connection not found
	 */
	public boolean assignConnectionToShard(long connectionId, long systemId) {
		int targetShardId = getShardForSystem(systemId);
		if (targetShardId == UNASSIGNED_SHARD) {
			Log.warn("assignConnectionToShard: System " + systemId + " has no shard mapping (UNASSIGNED). systemToShardMap size=" + systemToShardMap.size());
		}
		return moveConnectionToShard(connectionId, targetShardId);
	}

	/**
	 * Move a connection to a specific shard.
	 * Uses copy-then-remove pattern: brief duplication is safer than brief absence.
	 *
	 * @param connectionId The connection ID
	 * @param targetShardId The target shard ID
	 * @return true if moved, false if connection not found
	 */
	public boolean moveConnectionToShard(long connectionId, int targetShardId) {
		Integer currentShardId = connectionShardMap.get(connectionId);
		if (currentShardId == null) {
			return false;
		}

		if (currentShardId == targetShardId) {
			return true; // Already in correct shard
		}

		ConcurrentHashMap<Long, ServerConnectionHandler<T>> currentShard = shardedConnections.get(currentShardId);
		if (currentShard == null) {
			return false;
		}

		ServerConnectionHandler<T> handler = currentShard.get(connectionId);
		if (handler == null) {
			return false;
		}

		// Copy-then-remove pattern: add to new shard first
		ConcurrentHashMap<Long, ServerConnectionHandler<T>> targetShard = shardedConnections.computeIfAbsent(targetShardId, key -> new ConcurrentHashMap<>());
		targetShard.put(connectionId, handler);

		// Update reverse mapping
		connectionShardMap.put(connectionId, targetShardId);

		// Remove from old shard
		currentShard.remove(connectionId);

		Log.debug("Moved connection " + connectionId + " from shard " + currentShardId + " to shard " + targetShardId);

		return true;
	}

	/**
	 * Remove connection from its shard entirely.
	 *
	 * @param connectionId The connection ID to remove
	 * @return The removed handler, or null if not found
	 */
	public ServerConnectionHandler<T> removeConnection(long connectionId) {
		Integer shardId = connectionShardMap.remove(connectionId);
		if (shardId == null) {
			return null;
		}

		ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard = shardedConnections.get(shardId);
		if (shard == null) {
			return null;
		}

		return shard.remove(connectionId);
	}

	/**
	 * Get all connections in a specific shard for iteration.
	 * Returns a direct reference to the shard map for performance-critical loops.
	 *
	 * @param shardId The shard ID
	 * @return The shard's connection map (never null, may be empty)
	 */
	public ConcurrentHashMap<Long, ServerConnectionHandler<T>> getShardConnections(int shardId) {
		return shardedConnections.computeIfAbsent(shardId, key -> new ConcurrentHashMap<>());
	}

	/**
	 * Get a connection by ID from any shard.
	 *
	 * @param connectionId The connection ID
	 * @return The connection handler, or null if not found
	 */
	public ServerConnectionHandler<T> getConnection(long connectionId) {
		Integer shardId = connectionShardMap.get(connectionId);
		if (shardId == null) {
			return null;
		}
		ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard = shardedConnections.get(shardId);
		return shard != null ? shard.get(connectionId) : null;
	}

	/**
	 * Get all shard IDs.
	 */
	public Set<Integer> getShardIds() {
		return Collections.unmodifiableSet(shardedConnections.keySet());
	}

	/**
	 * Get total connection count across all shards.
	 */
	public int getTotalConnectionCount() {
		int count = 0;
		for (ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard : shardedConnections.values()) {
			count += shard.size();
		}
		return count;
	}

	/**
	 * Get connection count for a specific shard.
	 */
	public int getShardConnectionCount(int shardId) {
		ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard = shardedConnections.get(shardId);
		return shard != null ? shard.size() : 0;
	}

	/**
	 * Broadcast to a specific shard.
	 */
	public void broadcastToShard(int shardId, Packet packet) {
		ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard = shardedConnections.get(shardId);
		if (shard != null) {
			shard.forEach((id, handler) -> handler.sendTCP(packet));
		}
	}

	/**
	 * Broadcast to a specific shard with a condition.
	 */
	public void broadcastToShard(int shardId, Packet packet, Predicate<Connection<T>> condition) {
		ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard = shardedConnections.get(shardId);
		if (shard != null) {
			shard.forEach((id, handler) -> {
				if (condition.test(handler)) {
					handler.sendTCP(packet);
				}
			});
		}
	}

	/**
	 * Register a listener with a friendly name.
	 *
	 * Each listener gets a dedicated worker thread that processes events sequentially.
	 */
	public void addListener(Listener<T> listener, String name) {
		Objects.requireNonNull(listener, "listener");
		Objects.requireNonNull(name, "name");

		NamedListener<T> nl = new NamedListener<>(listener, name);
		listeners.add(nl);
		nl.start();
	}

	public void removeListener(Listener<T> listener) {
		Objects.requireNonNull(listener, "listener");

		listeners.removeIf(nl -> {
			if (nl.listener == listener) {
				nl.shutdown();
				return true;
			}
			return false;
		});
	}

	/**
	 * Returns ONLY the raw listener instances (no names) for legacy callers.
	 *
	 * Note: This returns a new list snapshot so callers can't mutate internal state.
	 */
	public java.util.ArrayList<Listener<T>> getListeners() {
		java.util.ArrayList<Listener<T>> out = new java.util.ArrayList<>();
		for (NamedListener<T> nl : listeners) {
			out.add(nl.listener);
		}
		return out;
	}

	public void dispatchChannelActive(ServerConnectionHandler<T> connection) {
		forEachListenerEnqueue(nl -> nl.listener.channelActive(connection), "channelActive", false);
	}

	public void dispatchChannelInactive(ServerConnectionHandler<T> connection) {
		forEachListenerEnqueue(nl -> nl.listener.channelInactive(connection), "channelInactive", false);
	}

	public void dispatchChannelExceptionCaught(ServerConnectionHandler<T> connection) {
		forEachListenerEnqueue(nl -> nl.listener.channelExceptionCaught(connection), "channelExceptionCaught", false);
	}

	public void dispatchChannelRead(ServerConnectionHandler<T> connection, Packet packet) {
		BiPredicate<Connection<T>, Packet> gate = packetGate;
		if (gate != null) {
			boolean admitted;
			try {
				admitted = gate.test(connection, packet);
			} catch (Throwable t) {
				Log.error("Packet gate failed on " + packet.getClass().getSimpleName() + "; dropping it", t);
				admitted = false;
			}
			if (!admitted) {
				Log.trace("Packet gate dropped " + packet.getClass().getSimpleName() + " (conn: " + connection.getConnectionID() + ")");
				return;
			}
		}
		forEachListenerEnqueue(nl -> nl.listener.channelRead(connection, packet), "channelRead", true);
	}

	/** See {@link #packetGate}. Null admits everything (the default). */
	public void setPacketGate(BiPredicate<Connection<T>, Packet> gate) {
		this.packetGate = gate;
	}

	/** See {@link #floodExemption}. Null exempts no connection (the default). */
	public void setFloodExemption(Predicate<Connection<T>> exemption) {
		this.floodExemption = exemption;
	}

	boolean isFloodExempt(Connection<T> connection) {
		Predicate<Connection<T>> exemption = floodExemption;
		if (exemption == null) {
			return false;
		}
		try {
			return exemption.test(connection);
		} catch (Throwable t) {
			Log.error("Flood exemption check failed (conn: " + connection.getConnectionID() + ")", t);
			return false;
		}
	}

	/**
	 * Sets the TCP proxies (HAProxy with send-proxy-v2 and the like) allowed to
	 * name a player's address with a PROXY protocol header, each an IP address or
	 * a CIDR block such as {@code 10.66.0.1} or {@code 10.66.0.0/24}. A connection
	 * from one of them must open with that header ({@link ProxyHeaderHandler});
	 * any other connection is direct and its first bytes go straight to TLS. Empty
	 * trusts none (the default). Call before {@link #start()}.
	 *
	 * @throws IllegalArgumentException for an entry that is not an IP literal or
	 *                                  CIDR block (hostnames are refused)
	 */
	public void setTrustedProxies(Collection<String> proxies) {
		List<IpSubnetFilterRule> rules = new ArrayList<>();
		for (String entry : proxies) {
			rules.add(subnetRule(entry.trim()));
		}
		this.trustedProxies = List.copyOf(rules);
		if (!rules.isEmpty()) {
			Log.info("Port " + port + ": PROXY protocol header required from " + proxies);
		}
	}

	static IpSubnetFilterRule subnetRule(String entry) {
		int slash = entry.indexOf('/');
		String ip = (slash < 0) ? entry : entry.substring(0, slash);
		boolean v4 = NetUtil.isValidIpV4Address(ip);
		if (!v4 && !NetUtil.isValidIpV6Address(ip)) {
			throw new IllegalArgumentException("Trusted proxy '" + entry + "' is not an IP address or CIDR block");
		}
		int prefix = (slash < 0) ? (v4 ? 32 : 128) : Integer.parseInt(entry.substring(slash + 1));
		return new IpSubnetFilterRule(ip, prefix, IpFilterRuleType.ACCEPT);
	}

	boolean isTrustedProxy(InetSocketAddress peer) {
		for (IpSubnetFilterRule rule : trustedProxies) {
			if (rule.matches(peer)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Counts {@code channel} against {@link #MAX_CONNECTIONS_PER_IP} for
	 * {@code ip} until it closes. False, with nothing counted, when that address
	 * is already full.
	 */
	boolean admitPerIp(Channel channel, String ip) {
		AtomicInteger ipCount = connectionsPerIp.computeIfAbsent(ip, key -> new AtomicInteger());
		if (ipCount.incrementAndGet() > MAX_CONNECTIONS_PER_IP) {
			ipCount.decrementAndGet();
			return false;
		}
		channel.closeFuture().addListener(future -> {
			AtomicInteger count = connectionsPerIp.get(ip);
			if (count != null && count.decrementAndGet() <= 0) {
				connectionsPerIp.remove(ip, count);
			}
		});
		return true;
	}

	@FunctionalInterface
	private interface NamedListenerAction<T> {
		void run(NamedListener<T> nl);
	}

	/**
	 * Enqueue a callback to every listener's dedicated thread.
	 *
	 * Each listener processes events sequentially, preserving ordering for that listener.
	 * A read is dropped when that listener's backlog of reads is full
	 * ({@link NamedListener#MAX_PENDING_READS}); connection lifecycle events are
	 * never dropped, so a session is always cleaned up.
	 */
	private void forEachListenerEnqueue(NamedListenerAction<T> action, String opName, boolean read) {
		for (NamedListener<T> nl : listeners) {
			Runnable task = () -> {
				try {
					action.run(nl);
				} catch (Throwable t) {
					Log.error("Listener [" + nl.name + "] failed during " + opName, t);
				}
			};
			if (read) {
				nl.enqueueRead(task);
			} else {
				nl.enqueue(task);
			}
		}
	}

	private static boolean isExpectedConnectionFailure(Throwable cause) {
		if (cause == null) return false;
		String msg = cause.getMessage();
		if (msg != null && (msg.contains("SslHandler removed") || msg.contains("Connection refused"))) return true;
		if (cause instanceof java.nio.channels.ClosedChannelException) return true;
		return false;
	}

	private static String describeTlsFailure(Throwable cause) {
		if (cause == null) {
			return "Unknown error";
		}

		String msg = cause.getMessage();

		if (msg != null && msg.contains("SslHandler removed before handshake completed")) {
			return "Client disconnected before TLS handshake completed (client may have dropped the connection)";
		}

		if (cause instanceof java.security.cert.CertificateException || (msg != null && msg.contains("certificate"))) {
			return "Certificate error - server.crt or server.key may be invalid or expired";
		}

		if (cause instanceof javax.net.ssl.SSLHandshakeException) {
			return "TLS handshake rejected - " + msg;
		}

		if (cause instanceof java.nio.channels.ClosedChannelException) {
			return "Client closed connection during TLS handshake";
		}

		return cause.getClass().getSimpleName() + ": " + msg;
	}

	/*
	 * ========================= Netty bootstrap =========================
	 */

	@Override
	public void run() {
		try {
			ServerBootstrap bootstrap = new ServerBootstrap();

			bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.option(io.netty.channel.ChannelOption.SO_BACKLOG, 1024)
					.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					InetSocketAddress peer = ch.remoteAddress();
					String remoteIp = (peer != null) ? peer.getAddress().getHostAddress() : "unknown";

					// Reject over-cap connections before spending any TLS handshake
					// or per-connection allocation cost.
					if (totalConnections.get() >= MAX_TOTAL_CONNECTIONS) {
						Log.debug("Rejecting connection from " + remoteIp + ": server-wide connection cap (" + MAX_TOTAL_CONNECTIONS + ") reached.");
						ch.close();
						return;
					}

					// A proxied connection is counted against its player's address
					// once the PROXY header names it (ProxyHeaderHandler): counted
					// here, every player behind the proxy would share one cap.
					boolean proxied = peer != null && isTrustedProxy(peer);
					if (!proxied && !admitPerIp(ch, remoteIp)) {
						Log.debug("Rejecting connection from " + remoteIp + ": per-IP connection cap (" + MAX_CONNECTIONS_PER_IP + ") reached.");
						ch.close();
						return;
					}
					totalConnections.incrementAndGet();
					ch.closeFuture().addListener(future -> totalConnections.decrementAndGet());

					ChannelPipeline pipeline = ch.pipeline();

					if (proxied) {
						pipeline.addLast(new HAProxyMessageDecoder());
						pipeline.addLast(new ProxyHeaderHandler(getServer()));
					}

					// TLS
					SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
					pipeline.addLast(sslHandler);

					// Log TLS handshake result with a clear message. Read the address
					// when it ends: by then a proxied connection's header has named
					// the player.
					sslHandler.handshakeFuture().addListener(future -> {
						String remoteAddr = String.valueOf(ProxyHeaderHandler.clientAddress(ch));
						if (future.isSuccess()) {
							Log.debug("TLS OK: " + remoteAddr);
						} else {
							Throwable cause = future.cause();
							String reason = describeTlsFailure(cause);
							if (isExpectedConnectionFailure(cause)) {
								Log.debug("TLS: " + remoteAddr + " - " + reason);
							} else {
								Log.error("TLS: " + remoteAddr + " - " + reason);
							}
						}
					});

					// Length-prefixed framing on the decrypted stream: matches the
					// [2-byte length][1-byte type][payload] wire format written by
					// Connection.sendTCP. Without this, a coalesced or fragmented
					// TCP/TLS read is handled as if it were exactly one application
					// packet, which desyncs under adversarial byte-level
					// fragmentation. lengthAdjustment=1 accounts for the 1-byte type
					// field between the length prefix and the counted payload; the
					// 65535+3 cap matches the wire's own 16-bit length field so a
					// declared length can never exceed what that field can represent.
					pipeline.addLast(new io.netty.handler.codec.LengthFieldBasedFrameDecoder(65535 + 3, 0, 2, 1, 0));

					// Timeouts
					pipeline.addLast(new ReadTimeoutHandler(timeout));
					pipeline.addLast(new WriteTimeoutHandler(timeout));

					// Connection object
					T connectionObject = connectionObjectSupplier.get();
					if (connectionObject == null) {
						throw new IllegalStateException("Supplier provided null connectionObject.");
					}

					// Handler
					ServerConnectionHandler<T> connectionHandler = new ServerConnectionHandler<>(getServer(), connectionObject);
					connectionHandler.setMarshalBufferSize(marshalBufferSize);

					connectionHandler.setClazzes(clazzes);

					pipeline.addLast(connectionHandler);

					// Confirm SSL is present or close.
					pipeline.addLast(new ChannelInboundHandlerAdapter() {
						@Override
						public void channelActive(ChannelHandlerContext ctx) throws Exception {
							if (ctx.pipeline().get(SslHandler.class) != null) {
								Log.debug("Both client and server have SSL/TLS enabled");
							} else {
								Log.debug("Either client or server does not have SSL/TLS enabled, disconnecting both.");
								ctx.close();
							}
						}
					});
				}
			});

			ChannelFuture future = bootstrap.bind(port).sync();
			Log.debug("Netty server started on port " + port);

			future.channel().closeFuture().sync();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			// Stop listener worker threads
			for (NamedListener<T> nl : listeners) {
				nl.shutdown();
			}

			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}
}
