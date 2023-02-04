package ctu.core.abstracts;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.util.Arrays;
import java.util.InputMismatchException;
import java.util.ListIterator;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.net.ssl.SSLException;

import ctu.core.Act;
import ctu.core.interfaces.Compression;
import ctu.core.interfaces.Crypt;
import ctu.core.interfaces.Listener;
import ctu.core.threads.CTURunnable;

public abstract class Connection extends CTURunnable {
	private CTU ctu;

	public enum Mode {
		SERVER, CLIENT
	}

	private long cid = 0;
	private long sid = -1;

	private DataOutputStream dataOutputStream;
	private DataInputStream dataInputStream;

	private Crypt crypt = new Crypt() {
	};

	private Compression compression = new Compression() {
		@Override
		public byte[] compress(byte[] bytes) throws IOException {
			return compressByteArray(bytes);
		}

		@Override
		public byte[] decompress(byte[] bytes) throws IOException {
			return decompressByteArray(bytes);
		}
	};

	private int padding = 0;

	private Mode mode = Mode.CLIENT;

	private Object object;

	public Connection(CTU ctu) {
		this.ctu = ctu;
	}

	public Packet bytesToPacket(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return null;
		}

		final byte[] index = new byte[1];

		for (int buf = 0; buf < index.length; ++buf) {
			index[buf] = bytes[buf + 2];
		}

		int id = (index[0] & 0xFF);

		if (id < 0 || id > ctu.getClazzes().size()) {
			System.out.println("Index out of range.");
			return null;
		}

		final Class<?> clazz = ctu.getClazzes().get(id);

		Packet packet = null;
		boolean process = true;

		try {
			packet = (Packet) clazz.getConstructor().newInstance();
		} catch (final InstantiationException e) {
			process = false;
			e.printStackTrace();
		} catch (final IllegalAccessException e) {
			process = false;
			e.printStackTrace();
		} catch (final IllegalArgumentException e) {
			process = false;
			e.printStackTrace();
		} catch (final InvocationTargetException e) {
			process = false;
			e.printStackTrace();
		} catch (final NoSuchMethodException e) {
			process = false;
			e.printStackTrace();
		} catch (final SecurityException e) {
			process = false;
			e.printStackTrace();
		} finally {
			if (process) {
				try {
					packet.unmarshal(compression.decompress(crypt.decrypt(Arrays.copyOfRange(bytes, 3, bytes.length))), 0);
				} catch (BufferUnderflowException e) {
					process = false;
					System.out.println("Process stopped underflow detected.");
				} catch (InputMismatchException e) {
				} catch (IOException e) {
				}
			}
		}

		return packet;
	}

	public Crypt getCrypt() {
		return crypt;
	}

	public DataInputStream getDataInputStream() {
		return dataInputStream;
	}

	public DataOutputStream getDataOutputStream() {
		return dataOutputStream;
	}

	public long getCID() {
		if (cid == 0) {
			mode = Mode.CLIENT;
		} else {
			mode = Mode.SERVER;
		}

		return cid;
	}

	public long getSID() {
		return sid;
	}

	public Mode getMode() {
		return mode;
	}

	public Socket getSocket() {
		return getSocket();
	}

	public byte[] recvTCP() {
		byte[] bytes = null;

		try {
			byte[] size = new byte[2];

			dataInputStream.read(size);

			int length = (size[0] & 0xFF) << 8 | (size[1] & 0xFF) + 1;

			byte[] data = new byte[length];

			dataInputStream.read(data);

			bytes = new byte[size.length + data.length];

			System.arraycopy(size, 0, bytes, 0, size.length);
			System.arraycopy(data, 0, bytes, size.length, data.length);
		} catch (final SocketException e) {
			for (final Listener listener : ctu.getListeners()) {
				listener.reset(this);
			}
			setRunning(false);
		} catch (final SocketTimeoutException e) {
			for (final Listener listener : ctu.getListeners()) {
				listener.timeout(this);
			}
			setRunning(false);
		} catch (final EOFException e) {
			e.printStackTrace();
			setRunning(false);
		} catch (final IOException e) {
			e.printStackTrace();
			setRunning(false);
		}

		return bytes;
	}

	@Override
	public void exec() {
		try {
			ctu.getSocket().setSoTimeout(ctu.getConfig().TIMEOUT);
		} catch (final SocketException e) {
			setRunning(false);
			e.printStackTrace();
		} finally {
			try {
				dataOutputStream = new DataOutputStream(ctu.getSocket().getOutputStream());

				setDataOutputStream(getDataOutputStream());
			} catch (final SocketTimeoutException e) {
				setRunning(false);
				e.printStackTrace();
			} catch (final StreamCorruptedException e) {
				setRunning(false);
				e.printStackTrace();
			} catch (final SSLException e) {
				setRunning(false);
				e.printStackTrace();
			} catch (final SocketException e) {
				setRunning(false);
				e.printStackTrace();
			} catch (final IOException e) {
				setRunning(false);
				e.printStackTrace();
			} finally {
				try {
					dataInputStream = new DataInputStream(ctu.getSocket().getInputStream());

					setDataInputStream(getDataInputStream());
				} catch (final SocketTimeoutException e) {
					setRunning(false);
					e.printStackTrace();
				} catch (final StreamCorruptedException e) {
					setRunning(false);
					e.printStackTrace();
				} catch (final SSLException e) {
					setRunning(false);
					e.printStackTrace();
				} catch (final SocketException e) {
					setRunning(false);
					e.printStackTrace();
				} catch (final IOException e) {
					setRunning(false);
					e.printStackTrace();
				} finally {
					for (final Listener listener : ctu.getListeners()) {
						listener.postConnect(this);
					}

					for (final Listener listener : ctu.getListeners()) {
						listener.connected(this);
					}

					while (ctu.isRunning() && isRunning()) {
						final byte[] bytes = recvTCP();

						if (bytes != null) {
							ctu.execute(new Act(this, bytes));
						}
					}

					for (final Listener listener : ctu.getListeners()) {
						listener.disconnected(this);
					}
				}
			}
		}
	}

	public void sendTCP(final Packet packet) {
		byte[] header = new byte[3];

		for (final ListIterator<Class<?>> iterator = ctu.getClazzes().listIterator(); iterator.hasNext();) {
			final int sentIndex = iterator.nextIndex();
			if (iterator.next().isInstance(packet)) {
				System.arraycopy(new byte[] { (byte) sentIndex }, 0, header, 2, 1);
				break;
			}
		}

		byte[] data = packet.getData(crypt, compression, ctu.getConfig());

		System.arraycopy(new byte[] { (byte) (data.length >>> 8), (byte) data.length }, 0, header, 0, 2);

		byte[] bytes = new byte[header.length + data.length];

		System.arraycopy(header, 0, bytes, 0, header.length);
		System.arraycopy(data, 0, bytes, header.length, data.length);

		try {
			if (this.dataOutputStream != null) {
				System.out.println((mode == Mode.CLIENT) ? String.format("[SENDING] %s [%s] to server.", packet.getClass().getSimpleName(), bytes.length, cid) : String.format("[SENDING] %s [%s] to client #%s (SID: %s).", packet.getClass().getSimpleName(), bytes.length, cid, sid));

				this.dataOutputStream.write(bytes);
			}
		} catch (final SSLException e) {
			setRunning(false);
		} catch (final SocketException e) {
			setRunning(false);
		} catch (final IOException e) {
			setRunning(false);
		}
	}

	public void setCrypt(Crypt crypt) {
		this.crypt = crypt;
	}

	public void setCompression(Compression compression) {
		this.compression = compression;
	}

	public void setCID(int cid) {
		this.cid = cid;
	}

	public void setSID(long sid) {
		this.sid = sid;
	}

	public void setPadding(int padding) {
		this.padding = padding;
	}

	public void start() {
		ctu.execute(this);
	}

	public Object getConnObject() {
		return object;
	}

	public void setConnObject(Object object) {
		this.object = object;
	}

	public CTU getCtu() {
		return ctu;
	}

	public byte[] compressByteArray(byte[] bytes) {
		ByteArrayOutputStream baos = null;
		Deflater dfl = new Deflater();

		dfl.setLevel(Deflater.FILTERED);
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

		} finally {
			try {
				if (baos != null)
					baos.close();
			} catch (Exception ex) {
			}
		}

		return baos.toByteArray();
	}

	public byte[] decompressByteArray(byte[] bytes) {
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

		} finally {
			try {
				if (baos != null)
					baos.close();
			} catch (Exception ex) {
			}
		}

		return baos.toByteArray();
	}
}
