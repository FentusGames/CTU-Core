package ctu.core.server;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;
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

	private final ConcurrentHashMap<Long, ServerConnectionHandler<T>> connectionMap = new ConcurrentHashMap<>();

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

	public ConcurrentHashMap<Long, ServerConnectionHandler<T>> getConnectionMap() {
		return connectionMap;
	}

	public long getNextConnectionId() {
		return connectionId++;
	}

	public void register(Class<?> clazz) {
		clazzes.put(key++, clazz);
	}

	public void broadcastTCP(Packet packet) {
		connectionMap.forEach((userID, connection) -> connection.sendTCP(packet));
	}

	public void broadcastTCP(Packet packet, Predicate<Connection<T>> condition) {
		connectionMap.forEach((userID, connection) -> {
			if (condition.test(connection)) {
				connection.sendTCP(packet);
			}
		});
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
					Log.debug("Listener [" + nl.name + "] failed during " + opName + ": " + t.getMessage());
					t.printStackTrace();
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
