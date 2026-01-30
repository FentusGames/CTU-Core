package ctu.core.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TransferToken {
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String DELIMITER = ".";

	public static String generate(long userId, String username, String fromServer, String toServer, long systemId, String secret, int expirySeconds) {
		long expiry = System.currentTimeMillis() + (expirySeconds * 1000L);
		String nonce = UUID.randomUUID().toString();

		String payload = String.format("%d|%s|%s|%s|%d|%d|%s", userId, username, fromServer, toServer, systemId, expiry, nonce);

		String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		String signature = sign(encodedPayload, secret);

		return encodedPayload + DELIMITER + signature;
	}

	public static TransferTokenPayload validate(String token, String secret) {
		if (token == null || token.isEmpty()) {
			return null;
		}

		int delimiterIndex = token.lastIndexOf(DELIMITER);
		if (delimiterIndex <= 0) {
			return null;
		}

		String encodedPayload = token.substring(0, delimiterIndex);
		String providedSignature = token.substring(delimiterIndex + 1);

		String expectedSignature = sign(encodedPayload, secret);
		if (!constantTimeEquals(expectedSignature, providedSignature)) {
			return null;
		}

		try {
			String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
			String[] parts = payload.split("\\|");

			if (parts.length != 7) {
				return null;
			}

			TransferTokenPayload result = new TransferTokenPayload();
			result.userId = Long.parseLong(parts[0]);
			result.username = parts[1];
			result.fromServerId = parts[2];
			result.toServerId = parts[3];
			result.systemId = Long.parseLong(parts[4]);
			result.expiry = Long.parseLong(parts[5]);
			result.nonce = parts[6];

			if (System.currentTimeMillis() > result.expiry) {
				return null;
			}

			return result;
		} catch (Exception e) {
			return null;
		}
	}

	private static String sign(String data, String secret) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
			mac.init(keySpec);
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException("Failed to compute HMAC", e);
		}
	}

	private static boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		if (a.length() != b.length()) {
			return false;
		}
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}

	public static class TransferTokenPayload {
		public long userId;
		public String username;
		public String fromServerId;
		public String toServerId;
		public long systemId;
		public long expiry;
		public String nonce;
	}
}
