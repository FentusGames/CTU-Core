package ctu.core.abstracts;

import java.io.IOException;
import java.util.Arrays;

import ctu.core.Config;
import ctu.core.interfaces.Compression;
import ctu.core.interfaces.Crypt;

public abstract class Packet {
	public abstract int marshal(byte[] buf, int offset);

	public abstract int unmarshal(byte[] buf, int offset);

	public abstract int unmarshal(byte[] buf, int offset, int end);

	public byte[] getData(Crypt crypt, Compression compression, Config config) {
		byte[] buf = new byte[config.PACKET_SIZE];

		byte[] out = null;

		try {
			out = compression.compress(Arrays.copyOf(buf, marshal(buf, 0)));
		} catch (IOException e) {
			e.printStackTrace();
		}

		return crypt.encrypt(out);
	}
}
