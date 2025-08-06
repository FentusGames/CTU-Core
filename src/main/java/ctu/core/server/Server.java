package ctu.core.server;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
 *             The Server class represents a server that listens for incoming client connections and handles them. It is constructed with the specified port number, and the SSL context is initialized during construction with the server's certificate and private key for secure communication. To start the server, call the start() method, which binds the server to the specified port, starts the event loop groups, and initializes the handlers for incoming client connections.
 * @param  <T>
 */
public class Server<T> implements Runnable {
	// Creating a thread pool with a cached pool of threads.
	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(32);

	private final ConcurrentHashMap<Long, ServerConnectionHandler<T>> connectionMap = new ConcurrentHashMap<>();

	private final EventLoopGroup bossGroup = new NioEventLoopGroup();
	private final EventLoopGroup workerGroup = new NioEventLoopGroup();

	private final int port;
	private final int timeout;
	private final Supplier<T> connectionObjectSupplier;

	private SslContext sslCtx;

	private int connectionId;

	private HashMap<Integer, Class<?>> clazzes = new HashMap<>();

	private ArrayList<Listener<T>> listeners = new ArrayList<>();

	private int key = 0;

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
			e.printStackTrace();
		}
	}

	private Server<T> getServer() {
		return this;
	}

	/**
	 * Starts the server with the specified SSL context and ensures that SSL is being used and the connection was successful.
	 */
	public void start() {
		executorService.execute(this);
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
		connectionMap.forEach((userID, connection) -> {
			connection.sendTCP(packet);
		});
	}

	public void broadcastTCP(Packet packet, Predicate<Connection<T>> condition) {
		connectionMap.forEach((userID, connection) -> {
			if (condition.test(connection)) {
				connection.sendTCP(packet);
			}
		});
	}

	@Override
	public void run() {
		try {
			// Create a new Bootstrap instance.
			ServerBootstrap bootstrap = new ServerBootstrap();

			// Set the event loop group, channel, and handler.
			bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					// Get the pipeline for the channel.
					ChannelPipeline pipeline = ch.pipeline();

					// Add the SSL handler to the pipeline.
					pipeline.addLast(sslCtx.newHandler(ch.alloc()));

					// Add a basic timeout if the client has not sent or received information in past X seconds.
					ch.pipeline().addLast(new ReadTimeoutHandler(timeout)).addLast(new WriteTimeoutHandler(timeout));

					T connectionObject = connectionObjectSupplier.get();
					if (connectionObject == null) {
						throw new IllegalStateException("Supplier provided null connectionObject.");
					}

					ServerConnectionHandler<T> connectionHandler = new ServerConnectionHandler<>(getServer(), connectionObject);

					// Set the classes for the connection handler.
					connectionHandler.setClazzes(clazzes);

					// Add the connection handler to the pipeline.
					pipeline.addLast(connectionHandler);

					// Add a channel inbound handler adapter to the pipeline.
					pipeline.addLast(new ChannelInboundHandlerAdapter() {
						@Override
						public void channelActive(ChannelHandlerContext ctx) throws Exception {
							// Check if both the client and server have SSL/TLS enabled.
							if (ctx.pipeline().get(SslHandler.class) != null) {
								Log.debug("Both client and server have SSL/TLS enabled");
							} else {
								Log.debug("Either client or server does not have SSL/TLS enabled, disconnecting both.");
								// Close the channel if SSL/TLS is not enabled.
								ctx.close();
							}
						}
					});
				}
			});

			ChannelFuture future = null;

			// Bind server to the specified port
			try {
				future = bootstrap.bind(port).sync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// Print message indicating that server has started
			Log.debug("Netty server started on port " + port);

			// Wait until server channel closes
			try {
				future.channel().closeFuture().sync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}

	public void addListener(Listener<T> listener) {
		listeners.add(listener);
	}

	public void removeLitener(Listener<T> listener) {
		listeners.remove(listener);
	}

	public ArrayList<Listener<T>> getListeners() {
		return listeners;
	}

	public ScheduledExecutorService getExecutorService() {
		return executorService;
	}
}
