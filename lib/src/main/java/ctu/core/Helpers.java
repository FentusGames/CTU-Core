package ctu.core;

import java.net.Socket;
import java.net.SocketException;

public class Helpers {
	public static int bytesToInt(final byte[] bytes) {
		return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
	}

	public static void setSoTimeout(Socket socket, int timeout) {
		try {
			socket.setSoTimeout(timeout);
		} catch (final SocketException e) {
			e.printStackTrace();
		}
	}
}
