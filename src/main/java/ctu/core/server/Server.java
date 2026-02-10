package ctu.core.server;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.net.ssl.SSLException;

import ctu.core.abstracts.Connection;
import ctu.core.abstracts.Packet;
import ctu.core.interfaces.Listener;
import ctu.core.logger.Log;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

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

	/** Shard ID -> connections in that shard */
	private final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, ServerConnectionHandler<T>>> shardedConnections = new ConcurrentHashMap<>();

	/** Connection ID -> Shard ID (reverse lookup for fast moves) */
	private final ConcurrentHashMap<Long, Integer> connectionShardMap = new ConcurrentHashMap<>();

	/** System ID -> Shard ID mapping (configured at startup) */
	private volatile Map<Long, Integer> systemToShardMap = Collections.emptyMap();

	private final EventLoopGroup bossGroup = new NioEventLoopGroup();
	private final EventLoopGroup workerGroup = new NioEventLoopGroup();

	private final int port;
	private final int timeout;
	private final Supplier<T> connectionObjectSupplier;

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
	 * Constructs a new Server object with the given port number. It also initializes the SSL context with the specified SSL provider, protocols, and the server's certificate and private key for secure communication.
	 *
	 * @param port                     the port to bind the server
	 * @param timeout                  timeout for read/write handlers in seconds
	 * @param connectionObjectSupplier supplier to create new non-null connection objects for each connection
	 */
	public Server(int port, int timeout, Supplier<T> connectionObjectSupplier) {
		this.port = port;
		this.timeout = timeout;
		this.connectionObjectSupplier = connectionObjectSupplier;

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
			shard.forEach((connId, handler) -> handler.sendTCP(packet));
		}
	}

	public void broadcastTCP(Packet packet, Predicate<Connection<T>> condition) {
		for (ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard : shardedConnections.values()) {
			shard.forEach((connId, handler) -> {
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
		ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard =
			shardedConnections.computeIfAbsent(shardId, k -> new ConcurrentHashMap<>());
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
		ConcurrentHashMap<Long, ServerConnectionHandler<T>> targetShard =
			shardedConnections.computeIfAbsent(targetShardId, k -> new ConcurrentHashMap<>());
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
		return shardedConnections.computeIfAbsent(shardId, k -> new ConcurrentHashMap<>());
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
			shard.forEach((connId, handler) -> handler.sendTCP(packet));
		}
	}

	/**
	 * Broadcast to a specific shard with a condition.
	 */
	public void broadcastToShard(int shardId, Packet packet, Predicate<Connection<T>> condition) {
		ConcurrentHashMap<Long, ServerConnectionHandler<T>> shard = shardedConnections.get(shardId);
		if (shard != null) {
			shard.forEach((connId, handler) -> {
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
		forEachListenerEnqueue(nl -> nl.listener.channelActive(connection), "channelActive");
	}

	public void dispatchChannelInactive(ServerConnectionHandler<T> connection) {
		forEachListenerEnqueue(nl -> nl.listener.channelInactive(connection), "channelInactive");
	}

	public void dispatchChannelExceptionCaught(ServerConnectionHandler<T> connection) {
		forEachListenerEnqueue(nl -> nl.listener.channelExceptionCaught(connection), "channelExceptionCaught");
	}

	public void dispatchChannelRead(ServerConnectionHandler<T> connection, Packet packet) {
		forEachListenerEnqueue(nl -> nl.listener.channelRead(connection, packet), "channelRead");
	}

	@FunctionalInterface
	private interface NamedListenerAction<T> {
		void run(NamedListener<T> nl);
	}

	/**
	 * Enqueue a callback to every listener's dedicated thread.
	 *
	 * Each listener processes events sequentially, preserving ordering for that listener.
	 */
	private void forEachListenerEnqueue(NamedListenerAction<T> action, String opName) {
		for (NamedListener<T> nl : listeners) {
			nl.enqueue(() -> {
				try {
					action.run(nl);
				} catch (Throwable t) {
					Log.error("Listener [" + nl.name + "] failed during " + opName, t);
				}
			});
		}
	}

	/*
	 * ========================= Netty bootstrap =========================
	 */

	@Override
	public void run() {
		try {
			ServerBootstrap bootstrap = new ServerBootstrap();

			bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline pipeline = ch.pipeline();

					// TLS
					pipeline.addLast(sslCtx.newHandler(ch.alloc()));

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
