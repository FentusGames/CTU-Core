package ctu.core.client;

import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import ctu.core.abstracts.Packet;
import ctu.core.callbacks.CallbackConnect;
import ctu.core.interfaces.Listener;
import ctu.core.logger.Log;
import ctu.core.packets.PacketPing;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * @author Fentus
 * 
 *         The Client class represents a client that connects to a server via
 *         SSL/TLS for secure communication. It initializes an SSL context with
 *         the specified SSL provider, protocols, and trust manager during
 *         construction. The start() method starts the client with the specified
 *         SSL context and ensures that SSL/TLS is being used and that the
 *         connection was successful.
 * @param <T>
 */
public class Client<T> implements Runnable {
	// Creating a thread pool with a cached pool of threads.
	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(32);

	private final String host;
	private final int port;
	private int timeout;
	private SslContext sslCtx;
	private long ping = 0;

	private final ClientConnectionHandler<T> connectionHandler = new ClientConnectionHandler<T>(this);
	private HashMap<Integer, Class<?>> clazzes = new HashMap<>();

	private Integer key = 0;

	private ChannelFuture future;

	private CallbackConnect callbackConnect;

	/**
	 * Constructs a new Client object with the given host and port. It also
	 * initializes the SSL context with the specified SSL provider, protocols, and
	 * trust manager for secure communication.
	 *
	 * @param host The hostname to connect to.
	 * @param port The port number to connect to.
	 */
	public Client(String host, int port, int timeout) {
		this.host = host;
		this.port = port;
		this.timeout = timeout;

		try {
			// @formatter:off
			this.sslCtx = SslContextBuilder
					.forClient()
					.sslProvider(SslProvider.JDK)
					.protocols("TLSv1.3")
					.trustManager(new File("server.crt"))
					.build();
			// @formatter:on
		} catch (SSLException e) {
			e.printStackTrace();
		}
	}
	
	public void start() {
		start(null);
	}

	/**
	 * Starts the client with the specified SSL context and ensures that SSL/TLS is
	 * being used and that the connection was successful.
	 */
	public void start(CallbackConnect callbackConnect) {
		this.callbackConnect = callbackConnect;
		
		executorService.execute(this);
		executorService.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				getClient().sendTCP(new PacketPing().withTime(Instant.now()));
				Log.debug(String.format(String.format("Ping (M/S): %.2f", ping / 1_000_000.0F)));
			}
		}, (int) (timeout * 0.8F), (int) (timeout * 0.8F), TimeUnit.SECONDS);
	}

	public void sendTCP(Packet packet) {
		connectionHandler.sendTCP(packet);
	}

	public void sendUDP(Packet packet) {
		connectionHandler.sendUDP(packet);
	}

	public void register(Class<?> clazz) {
		clazzes.put(key++, clazz);
	}

	@Override
	public void run() {
		// Create a new event loop group.
		NioEventLoopGroup group = new NioEventLoopGroup();

		try {
			// Create a new Bootstrap instance.
			Bootstrap bootstrap = new Bootstrap();

			// Set the event loop group, channel, and handler.
			bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					// Get the pipeline for the channel.
					ChannelPipeline pipeline = ch.pipeline();

					// Add the SSL handler to the pipeline.
					pipeline.addLast(sslCtx.newHandler(ch.alloc(), host, port));

					// Add a basic timeout if the client has not sent or received information in
					// past X seconds.
					ch.pipeline().addLast(new ReadTimeoutHandler(timeout)).addLast(new WriteTimeoutHandler(timeout));

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

			// Connect to the host and port.
			future = bootstrap.connect(host, port);

			// Add a future listener to check if the connection was successful.
			future.addListener(new FutureListener<Void>() {
				@Override
				public void operationComplete(Future<Void> future) throws Exception {
					if (future.isSuccess()) {
						Log.debug("Connection success");
					} else {
						Log.debug("Connection failed");

						// Exit the program if the connection failed.
						System.exit(0);
					}
					
					if (callbackConnect != null) {
						callbackConnect.execute(future.isSuccess());
					}
				}
			});

			try {
				future.channel().closeFuture().sync();
			} catch (InterruptedException e) {

			}
		} finally {
			group.shutdownGracefully();
		}
	}

	public Client<T> getClient() {
		return this;
	}

	public void addListener(Listener<T> listener) {
		connectionHandler.addListener(listener);
	}

	public void removeListener(Listener<T> listener) {
		connectionHandler.removeListener(listener);
	}

	public void setPing(long ping) {
		this.ping = ping;
	}

	public void close() {
		executorService.shutdown(); // initiate shutdown

		try {
			// wait for all tasks to finish or timeout after 10 seconds
			if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
				executorService.shutdownNow(); // force shutdown if tasks are still running after 10 seconds
			}
		} catch (InterruptedException ex) {
			executorService.shutdownNow(); // force shutdown if waiting is interrupted
		}

		try {
			if (future != null) {
				future.channel().close().sync();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public ScheduledExecutorService getExecutorService() {
		return executorService;
	}
}
