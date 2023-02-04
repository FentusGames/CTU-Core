package ctu.core.interfaces;

public interface Crypt {
	default byte[] decrypt(byte[] bytes) {
		return bytes;
	}

	default byte[] encrypt(byte[] bytes) {
		return bytes;
	}
}
