package examples.keys;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class GenerateSSLCerts {
	public static void main(String[] args) throws Exception {
		// Adds the BouncyCastle provider to the list of security providers
		Security.addProvider(new BouncyCastleProvider());

		// Default parameters for certificate generation
		String owner = "CN=localhost"; // Common Name for the certificate owner
		int validityDays = 365; // How long the certificate is valid for
		int keySize = 2048; // Size of RSA key in bits

		// Parse command-line arguments to override default parameters
		if (args.length > 0) {
			owner = args[0];
		}
		if (args.length > 1) {
			try {
				validityDays = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				System.err.println("Invalid validity days format. Using default.");
			}
		}
		if (args.length > 2) {
			try {
				keySize = Integer.parseInt(args[2]);
			} catch (NumberFormatException e) {
				System.err.println("Invalid key size format. Using default 2048.");
			}
		}

		// Generate RSA Key Pair
		KeyPair keyPair = generateRSAKeyPair(keySize);

		// Generate a self-signed X.509 certificate
		X509Certificate certificate = generateSelfSignedCertificate(keyPair, owner, validityDays);

		// Save the private key and certificate to files
		savePrivateKey(keyPair.getPrivate(), "server.key");
		saveCertificate(certificate, "server.crt");

		// Output the generation details
		System.out.println("Certificate and private key generated successfully with the following settings:");
		System.out.println("Owner (CN): " + owner);
		System.out.println("Validity: " + validityDays + " days");
		System.out.println("RSA Key Size: " + keySize + " bits");
		System.out.println("Files saved: server.key (Private Key), server.crt (Certificate)");
	}

	// Generates RSA key pair with specified key size
	private static KeyPair generateRSAKeyPair(int keySize) throws NoSuchAlgorithmException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(keySize);
		return generator.generateKeyPair();
	}

	// Generates a self-signed X.509 certificate
	private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, String owner, int validityDays) throws Exception {
		long now = System.currentTimeMillis();
		Date startDate = new Date(now);
		X500Name ownerName = new X500Name(owner);
		BigInteger serial = BigInteger.valueOf(now);
		Date endDate = new Date(now + (long) validityDays * 24 * 60 * 60 * 1000); // End date based on validity period

		JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(ownerName, serial, startDate, endDate, ownerName, keyPair.getPublic());

		ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());

		return new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(builder.build(signer));
	}

	// Saves the private key to a file using PEM format
	private static void savePrivateKey(PrivateKey privateKey, String filename) throws IOException {
		try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(filename))) {
			pemWriter.writeObject(privateKey);
		}
	}

	// Saves the certificate to a file using PEM format
	private static void saveCertificate(X509Certificate certificate, String filename) throws IOException, CertificateEncodingException {
		try (FileWriter fw = new FileWriter(filename); JcaPEMWriter pemWriter = new JcaPEMWriter(fw)) {
			pemWriter.writeObject(certificate);
		}
	}
}
