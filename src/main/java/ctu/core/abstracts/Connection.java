package ctu.core.abstracts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.BufferUnderflowException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Objects;
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
		if (bytes == null || bytes.length == 0) {
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
		} catch (final InputMismatchException | IOException e) {
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
			ByteArrayOutputStream baos = null;
			Deflater dfl = new Deflater();

			dfl.setLevel(Deflater.BEST_SPEED); // TODO: Evaluate this.
			dfl.setInput(bytes);
			dfl.finish();

			baos = new ByteArrayOutputStream();

			byte[] tmp = new byte[4 * 1024];

			try {
				while (!dfl.finished()) {
					int size = dfl.deflate(tmp);
					baos.write(tmp, 0, size);
				}
			} catch (Exception ex) {
				Log.debug("Compression stream error: " + ex.getMessage());
			} finally {
				try {
					if (baos != null)
						baos.close();
				} catch (Exception ex) {
					Log.debug("Compression stream error: " + ex.getMessage());
				}
			}

			return baos.toByteArray();
		}

		@Override
		public byte[] decompress(byte[] bytes) throws IOException {
			ByteArrayOutputStream baos = null;
			Inflater iflr = new Inflater();

			iflr.setInput(bytes);

			baos = new ByteArrayOutputStream();

			byte[] tmp = new byte[4 * 1024];

			try {
				while (!iflr.finished()) {
					int size = iflr.inflate(tmp);
					baos.write(tmp, 0, size);
				}
			} catch (Exception ex) {
				Log.debug("Compression stream error: " + ex.getMessage());
			} finally {
				try {
					if (baos != null)
						baos.close();
				} catch (Exception ex) {
					Log.debug("Compression stream error: " + ex.getMessage());
				}
			}

			return baos.toByteArray();
		}
	};

	/**
	 * packetToBytes takes a Compression object as an argument and returns a compressed version of the marshalled packet data. The method compresses the packet data using the compress method of the Compression object, and then checks whether the compressed data exceeds the MTU size limit of 1500 bytes. If the compressed data is larger than the MTU size, the method prints a warning message to the console.
	 *
	 * Note that the default buffer size for the byte array used in the getData method is 4096 bytes. Subclasses may override this value if necessary.
	 * 
	 * @param  compression
	 * @return
	 */
	public byte[] packetToBytes(Compression compression, Packet packet) {
		byte[] buf = new byte[4096];

		byte[] out = null;

		try {
			out = compression.compress(Arrays.copyOf(buf, packet.marshal(buf, 0)));
		} catch (IOException e) {
			Log.error("Packet compression error", e);
		}

		if (out.length >= 1500) {
			Log.debug("Packets should not exceed 1500 bytes after compression.");
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

		int key = clazzesStringInteger.get(packet.getClass().getSimpleName());

		System.arraycopy(new byte[] { (byte) key }, 0, header, 2, 1);

		byte[] data = packetToBytes(compression, packet);

		System.arraycopy(new byte[] { (byte) (data.length >>> 8), (byte) data.length }, 0, header, 0, 2);

		byte[] bytes = new byte[header.length + data.length];

		System.arraycopy(header, 0, bytes, 0, header.length);
		System.arraycopy(data, 0, bytes, header.length, data.length);

		String packetName = packet.getClass().getSimpleName();
		int size = bytes.length;

		if (ctx == null) {
			Log.debug("TCP send failed (not connected) - Packet: " + packetName + ", Size: " + size + " bytes.");
		} else {
			ChannelFuture future = ctx.writeAndFlush(Unpooled.copiedBuffer(bytes));

			future.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) {
					if (!future.isSuccess()) {
						Log.debug("TCP send failed: " + future.cause().getMessage() + " - Packet: " + packetName + ", Size: " + size + " bytes.");
					}
				}
			});
		}

		Log.trace("Sent TCP packet: " + packetName + ", Size: " + size + " bytes.");
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
}
