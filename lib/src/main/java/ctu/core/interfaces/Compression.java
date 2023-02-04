package ctu.core.interfaces;

import java.io.IOException;

public interface Compression {
	default byte[] compress(byte[] bytes) throws IOException {
		return bytes;
	}

	default byte[] decompress(byte[] bytes) throws IOException {
		return bytes;
	}
}
