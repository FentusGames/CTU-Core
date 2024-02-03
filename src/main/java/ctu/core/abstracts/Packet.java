package ctu.core.abstracts;

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
}
