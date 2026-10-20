package ctu.core.abstracts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.BufferUnderflowException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import ctu.core.interfaces.Compression;
import ctu.core.logger.Log;
import ctu.core.server.Server;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author     Fentus
 * 
 *             The Connection class is an abstract class that provides methods to handle connection data such as compression, decompression, sending TCP and UDP packets, and converting bytes to packets. The class also contains a list of acceptable classes that it can check against.
 * @param  <T>
 */
public class Connection<T> extends SimpleChannelInboundHandler<ByteBuf> {
	private long connectionID = -1;
	private long userID = -1;
	private final T connectionObject;
	private boolean inactive = false;

	// This field is a list of acceptable classes that the Connection class can check against when handling packets.
	private HashMap<Integer, Class<?>> clazzesIntegerClazz = new HashMap<>();
	private HashMap<String, Integer> clazzesStringInteger = new HashMap<>();

	// Bandwidth tracking per packet type: packetName -> [count, totalBytes]
	private final ConcurrentHashMap<String, long[]> packetBytesSent = new ConcurrentHashMap<>();
	private final AtomicLong totalBytesSent = new AtomicLong();
	private final AtomicLong totalBytesReceived = new AtomicLong();

	// This field is an instance of the ChannelHandlerContext class that represents the context of the Netty channel.
	// It is used to send packets to the remote address.
	private ChannelHandlerContext ctx;

	/**
	 * Constructs a new Connection with the given non-null connection object.
	 * 
	 * @param connectionObject the connection object associated with this connection
	 */
	public Connection(T connectionObject) {
		this.connectionObject = Objects.requireNonNull(connectionObject, "connectionObject must not be null");
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		this.ctx = ctx;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {

	}

	/**
	 * This method takes an array of bytes and converts it into a Packet object. It returns null if the bytes array is null or has a length of 0. Otherwise, it extracts the index and the associated class from the list of acceptable classes based on the index. Then, it instantiates the class and uses its unmarshal() method to deserialize the packet data. The deserialization is performed by first decompressing the byte array and then calling the unmarshal() method.
	 * 
	 * @param  bytes
	 * @return
	 */
	public Packet bytesToPacket(byte[] bytes) {
		if (bytes == null || bytes.length < 3) {
			return null;
		}

		final byte[] index = new byte[1];

		for (int buf = 0; buf < index.length; ++buf) {
			index[buf] = bytes[buf + 2];
		}

		int id = (index[0] & 0xFF);

		if (id < 0 || id > clazzesIntegerClazz.size()) {
			Log.debug("Index out of range.");
			return null;
		}

		final Class<?> clazz = clazzesIntegerClazz.get(id);

		Packet packet = null;

		try {
			packet = (Packet) clazz.getConstructor().newInstance();
			packet.unmarshal(compression.decompress(Arrays.copyOfRange(bytes, 3, bytes.length)), 0);
		} catch (final InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException | BufferUnderflowException e) {
			Log.error("Packet instantiation/unmarshal error", e);
		} catch (final InputMismatchException | IOException | ArrayIndexOutOfBoundsException | NegativeArraySizeException e) {
			Log.debug("Packet parse error: " + e.getMessage());
		}

		return packet;
	}

	/**
	 * This field is an instance of the Compression interface that provides methods for compressing and decompressing data. It is used to compress and decompress packet data before and after transmission.
	 */
	private Compression compression = new Compression() {
		@Override
		public byte[] compress(byte[] bytes) throws IOException {
			// The thread's own deflater, reset rather than made: a new one per packet
			// set up and tore down zlib's native state (some 256 KB) every time, and
			// with a big fight in view a tick sends hundreds of batches - most of the
			// sending thread's time (thread dumps, 2026-10-20). A reset deflater
			// writes the same stream a new one would.
			Deflater dfl = DEFLATER.get();
			dfl.reset();
			try {
				dfl.setInput(bytes);
				dfl.finish();

				ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length / 2 + 64);
				byte[] tmp = CHUNK.get();

				while (!dfl.finished()) {
					int size = dfl.deflate(tmp);
					baos.write(tmp, 0, size);
				}

				return baos.toByteArray();
			} finally {
				// Let go of the input now, not at the thread's next packet.
				dfl.reset();
			}
		}

		@Override
		public byte[] decompress(byte[] bytes) throws IOException {
			// The thread's own inflater, as compress() keeps its deflater.
			Inflater iflr = INFLATER.get();
			iflr.reset();
			try {
				iflr.setInput(bytes);

				ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length * 3 + 64);
				byte[] tmp = CHUNK.get();
				int total = 0;

				while (!iflr.finished()) {
					int size = iflr.inflate(tmp);
					if (size == 0 && iflr.needsInput()) {
						// No more compressed input to consume and stream never
						// signalled finished() - malformed/truncated data.
						break;
					}
					total += size;
					if (total > MAX_DECOMPRESSED_SIZE) {
						throw new IOException("Decompressed packet exceeds " + MAX_DECOMPRESSED_SIZE
								+ " bytes - refusing (possible decompression bomb).");
					}
					baos.write(tmp, 0, size);
				}

				return baos.toByteArray();
			} catch (IOException ex) {
				throw ex;
			} catch (Exception ex) {
				throw new IOException("Decompression error", ex);
			} finally {
				iflr.reset();
			}
		}
	};

	/**
	 * Each thread's zlib streams and scratch, reused packet after packet (see
	 * compress()). Per thread, not per connection: a server sends to one
	 * connection from its tick workers and its event loop at once. Never ended:
	 * one per sending thread for the life of the thread, and the JDK frees its
	 * native memory when the thread is gone.
	 */
	private static final ThreadLocal<Deflater> DEFLATER = ThreadLocal.withInitial(() -> new Deflater(Deflater.BEST_SPEED));
	private static final ThreadLocal<Inflater> INFLATER = ThreadLocal.withInitial(Inflater::new);
	private static final ThreadLocal<byte[]> CHUNK = ThreadLocal.withInitial(() -> new byte[4 * 1024]);
	/** The thread's marshal buffer, grown to the largest {@link #marshalBufferSize} it has been asked for. */
	private static final ThreadLocal<byte[]> MARSHAL = new ThreadLocal<>();

	/**
	 * Hard ceiling on decompressed packet size, enforced during inflate before any
	 * bytes reach Colfer's unmarshal. The largest legitimate marshalled packet
	 * project-wide is capped at colferSizeMax = 4098 bytes (see
	 * PacketClientUpdateBatch), so this leaves generous headroom while still
	 * refusing a tiny malicious payload that would otherwise inflate to gigabytes.
	 */
	private static final int MAX_DECOMPRESSED_SIZE = 64 * 1024;

	/**
	 * packetToBytes takes a Compression object as an argument and returns a compressed version of the marshalled packet data. The method compresses the packet data using the compress method of the Compression object, and then checks whether the compressed data exceeds the MTU size limit of 1500 bytes. If the compressed data is larger than the MTU size, the method prints a warning message to the console.
	 *
	 * Note that the default buffer size for the byte array used in the getData method is 4096 bytes. Subclasses may override this value if necessary.
	 * 
	 * @param  compression
	 * @return
	 */
	/** Default marshal buffer size used when callers don't explicitly configure one. */
	public static final int DEFAULT_MARSHAL_BUFFER_SIZE = 4096;

	private int marshalBufferSize = DEFAULT_MARSHAL_BUFFER_SIZE;

	/**
	 * Configure the per-packet marshal buffer size. Must be ≥ the largest expected
	 * marshalled packet size (i.e. ≥ the largest packet type's colferSizeMax). The default
	 * (4 KiB) suits small packets; raise it on Client/Server when batching large bundled
	 * packets like PacketPositionBatch with raised colferSizeMax.
	 */
	public void setMarshalBufferSize(int size) {
		if (size > 0) this.marshalBufferSize = size;
	}

	public int getMarshalBufferSize() {
		return marshalBufferSize;
	}

	public byte[] packetToBytes(Compression compression, Packet packet) {
		// Sized via {@link #setMarshalBufferSize}; configured per-Client/Server at construction time.
		// The thread's own, as the deflater is: only the marshalled bytes are copied out of it.
		byte[] buf = MARSHAL.get();
		if (buf == null || buf.length < marshalBufferSize) {
			buf = new byte[marshalBufferSize];
			MARSHAL.set(buf);
		}
		byte[] out = null;

		try {
			out = compression.compress(Arrays.copyOf(buf, packet.marshal(buf, 0)));
		} catch (IOException e) {
			Log.error("Packet compression error", e);
		}

		if (out == null) {
			return null;
		}

		if (out.length > 1500) {
			Log.warn("[WARN][PACKET_OVERSIZE] " + packet.getClass().getSimpleName()
					+ " is " + out.length + " bytes compressed, exceeds MTU 1500 - will fragment.");
		}

		return out;
	}

	/**
	 * This method sends a TCP packet containing the given Packet object. It creates a byte array that contains a header and the packet data, where the header is the same as in the sendUDP() method. The method then sends the data using the Netty channel.
	 * 
	 * @param  packet
	 * @return
	 */
	public void sendTCP(Packet packet) {
		if (isInactive()) {
			return;
		}

		byte[] header = new byte[3];

		Integer key = clazzesStringInteger.get(packet.getClass().getSimpleName());

		if (key == null) {
			Log.debug("Cannot send unregistered packet: " + packet.getClass().getSimpleName());
			return;
		}

		System.arraycopy(new byte[] { (byte) (int) key }, 0, header, 2, 1);

		byte[] data = packetToBytes(compression, packet);

		if (data == null) {
			Log.debug("Failed to serialize packet: " + packet.getClass().getSimpleName());
			return;
		}

		System.arraycopy(new byte[] { (byte) (data.length >>> 8), (byte) data.length }, 0, header, 0, 2);

		byte[] bytes = new byte[header.length + data.length];

		System.arraycopy(header, 0, bytes, 0, header.length);
		System.arraycopy(data, 0, bytes, header.length, data.length);

		String packetName = packet.getClass().getSimpleName();
		int size = bytes.length;

		// Track bandwidth per packet type
		packetBytesSent.computeIfAbsent(packetName, unused -> new long[2]);
		long[] stats = packetBytesSent.get(packetName);
		stats[0]++;
		stats[1] += size;
		totalBytesSent.addAndGet(size);

		if (ctx == null) {
			Log.debug("TCP send failed (not connected) - Packet: " + packetName + ", Size: " + size + " bytes.");
		} else {
			// Wrapped, not copied: the array is this send's own and nothing touches it again.
			ChannelFuture future = ctx.writeAndFlush(Unpooled.wrappedBuffer(bytes));

			future.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) {
					if (!future.isSuccess()) {
						Log.debug("TCP send failed: " + future.cause().getMessage() + " - Packet: " + packetName + ", Size: " + size + " bytes.");
					}
				}
			});
		}

		if (Log.isTraceEnabled()) Log.trace("Sent TCP packet: " + packetName + ", Size: " + size + " bytes.");
	}

	/**
	 * This method is used to set the list of acceptable classes that the Connection class can check against. It takes an ArrayList of Class<?> as a parameter and assigns it to the "clazzes" member variable of the Connection class.
	 * 
	 * @param clazzes
	 */
	public void setClazzes(HashMap<Integer, Class<?>> clazzes) {
		clazzes.forEach((key, value) -> {
			String name = value.getSimpleName();

			Log.debug(String.format("Packet %s set to key %s", name, key));

			clazzesIntegerClazz.put(key, value);
			clazzesStringInteger.put(name, key);
		});
	}

	protected void setConnectionID(long connectionID) {
		this.connectionID = connectionID;
	}

	public long getConnectionID() {
		return connectionID;
	}

	public long getUserID() {
		return userID;
	}

	public void setUserID(long userID) {
		this.userID = userID;
	}

	public T getConnectionObject() {
		return connectionObject;
	}

	public ChannelHandlerContext getCtx() {
		return ctx;
	}

	public boolean isInactive() {
		return inactive;
	}

	public void setInactive(boolean inactive) {
		this.inactive = inactive;
	}

	public void remove(Server<T> server) {
		server.removeConnection(connectionID);
	}

	public Map<String, long[]> getPacketBytesSent() {
		return packetBytesSent;
	}

	public long getTotalBytesSent() {
		return totalBytesSent.get();
	}

	public long getTotalBytesReceived() {
		return totalBytesReceived.get();
	}

	protected void addBytesReceived(int size) {
		totalBytesReceived.addAndGet(size);
	}
}
