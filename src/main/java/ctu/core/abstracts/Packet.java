package ctu.core.abstracts;

import java.io.IOException;
import java.util.Arrays;

import ctu.core.interfaces.Compression;
import ctu.core.logger.Log;

/**
 * The Packet class is intended to be extended by any class that needs to be sent via a connection. This class provides
 * automatic marshaling and unmarshaling of packet data, as well as warning when packets are hitting the maximum
 * transmission unit (MTU) size limit.
 * 
 * @author Fentus
 */
public abstract class Packet {
	// A logger can be added to the class to allow logging of the warning message when the packet size is too large.

	// Marshals the packet data into a byte array starting at the given offset.
	public abstract int marshal(byte[] buf, int offset);

	// Unmarshals the packet data from a byte array starting at the given offset.
	public abstract int unmarshal(byte[] buf, int offset);

	// Unmarshals the packet data from a byte array starting at the given offset and ending at the specified end index.
	public abstract int unmarshal(byte[] buf, int offset, int end);

	/**
	 * getData takes a Compression object as an argument and returns a compressed version of the marshalled packet data.
	 * The method compresses the packet data using the compress method of the Compression object, and then checks
	 * whether the compressed data exceeds the MTU size limit of 1500 bytes. If the compressed data is larger than the
	 * MTU size, the method prints a warning message to the console.
	 *
	 * Note that the default buffer size for the byte array used in the getData method is 4096 bytes. Subclasses may
	 * override this value if necessary.
	 * 
	 * @param  compression
	 * @return
	 */
	public byte[] getData(Compression compression) {
		byte[] buf = new byte[4096];

		byte[] out = null;

		try {
			out = compression.compress(Arrays.copyOf(buf, marshal(buf, 0)));
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (out.length >= 1500) {
			Log.debug("Packets should not exceed 1500 bytes after compression.");
		}

		return out;
	}
}
