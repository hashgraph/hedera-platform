/*
 * (c) 2016-2022 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.crypto;

import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.platform.SettingsProvider;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * used to create and receive TLS connections, based on the given trustStore
 */
public class TlsFactory implements SocketFactory {
	private final SettingsProvider settings;
	private final SSLServerSocketFactory sslServerSocketFactory;
	private final SSLSocketFactory sslSocketFactory;

	/**
	 * Construct this object to create and receive TLS connections. This is done using the trustStore
	 * whose reference was passed in as an argument. That trustStore must contain certs for all
	 * the members before calling this constructor. This method will then create the appropriate
	 * KeyManagerFactory, TrustManagerFactory, SSLContext, SSLServerSocketFactory, and SSLSocketFactory, so
	 * that it can later create the TLS sockets.
	 */
	public TlsFactory(final KeysAndCerts keysAndCerts, final SettingsProvider settings)
			throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, KeyManagementException,
			CertificateException, IOException {
		this.settings = settings;
		final char[] password = settings.getKeystorePassword().toCharArray();
		/* nondeterministic CSPRNG */
		final SecureRandom nonDetRandom = getNonDetRandom();

		// the agrKeyStore should contain an entry with both agrKeyPair.getPrivate() and agrCert
		// PKCS12 uses file extension .p12 or .pfx
		final KeyStore agrKeyStore = KeyStore.getInstance(CryptoConstants.KEYSTORE_TYPE);
		agrKeyStore.load(null, null); // initialize
		agrKeyStore.setKeyEntry("key", keysAndCerts.agrKeyPair().getPrivate(), password,
				new Certificate[] { keysAndCerts.agrCert() });

		// "PKIX" may be more interoperable than KeyManagerFactory.getDefaultAlgorithm or
		// TrustManagerFactory.getDefaultAlgorithm(), which was "SunX509" on one system tested
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(CryptoConstants.KEY_MANAGER_FACTORY_TYPE);
		keyManagerFactory.init(agrKeyStore, password);
		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
				CryptoConstants.TRUST_MANAGER_FACTORY_TYPE);
		trustManagerFactory.init(keysAndCerts.publicStores().sigTrustStore());
		SSLContext sslContext = SSLContext.getInstance(CryptoConstants.SSL_VERSION);
		SSLContext.setDefault(sslContext);
		sslContext.init(keyManagerFactory.getKeyManagers(),
				trustManagerFactory.getTrustManagers(),
				nonDetRandom);
		sslServerSocketFactory = sslContext.getServerSocketFactory();
		sslSocketFactory = sslContext.getSocketFactory();
	}

	/**
	 * Return the nondeterministic secure random number generator stored in this Crypto instance. If it
	 * doesn't already exist, create it.
	 *
	 * @return the stored SecureRandom object
	 */
	private static SecureRandom getNonDetRandom() {
		final SecureRandom nonDetRandom;
		try {
			nonDetRandom = SecureRandom.getInstanceStrong();
		} catch (NoSuchAlgorithmException e) {
			throw new CryptographyException(e, EXCEPTION);
		}
		// call nextBytes before setSeed, because some algorithms (like SHA1PRNG) become
		// deterministic if you don't. This call might hang if the OS has too little entropy
		// collected. Or it might be that nextBytes doesn't hang but getSeed does. The behavior is
		// different for different choices of OS, Java version, and JDK library implementation.
		nonDetRandom.nextBytes(new byte[1]);
		return nonDetRandom;
	}

	@Override
	public ServerSocket createServerSocket(final byte[] ipAddress, final int port) throws IOException {
		final SSLServerSocket serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket();
		serverSocket.setEnabledCipherSuites(new String[] { CryptoConstants.TLS_SUITE });
		serverSocket.setWantClientAuth(true);
		serverSocket.setNeedClientAuth(true);
		SocketFactory.configureAndBind(serverSocket, settings, ipAddress, port);
		return serverSocket;
	}

	@Override
	public Socket createClientSocket(final String ipAddress, final int port) throws IOException {
		SSLSocket clientSocket = (SSLSocket) sslSocketFactory.createSocket();
		// ensure the connection is ALWAYS the exact cipher suite we've chosen
		clientSocket.setEnabledCipherSuites(new String[] { CryptoConstants.TLS_SUITE });
		clientSocket.setWantClientAuth(true);
		clientSocket.setNeedClientAuth(true);
		SocketFactory.configureAndConnect(clientSocket, settings, ipAddress, port);
		clientSocket.startHandshake();
		return clientSocket;
	}
}
