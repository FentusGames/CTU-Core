package ctu.core.interfaces;

import java.io.IOException;

/**
 * This code presents an interface named "Compression" that provides two default methods for compressing and
 * decompressing a byte array. This interface is designed to allow developers to easily replace the implementation of
 * the compression and decompression methods as needed.
 * 
 * @author Fentus
 */

public interface Compression {
	default byte[] compress(byte[] bytes) throws IOException {
		return bytes;
	}

	default byte[] decompress(byte[] bytes) throws IOException {
		return bytes;
	}
}
