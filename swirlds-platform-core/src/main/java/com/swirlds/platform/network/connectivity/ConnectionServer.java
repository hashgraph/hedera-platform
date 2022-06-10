/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */
package com.swirlds.platform.network.connectivity;

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * Listens on a server socket for incoming connections. All new connections are passed on to the supplied handler.
 */
public class ConnectionServer implements InterruptableRunnable {
	/** number of milliseconds to sleep when a server socket binds fails until trying again */
	private static final int SLEEP_AFTER_BIND_FAILED_MS = 100;
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();
	/** overrides ip if null */
	private static final byte[] LISTEN_IP = new byte[] { 0, 0, 0, 0 };

	/** the IP address that this server listens on for establishing new connections */
	private final byte[] ip;
	/** the port that this server listens on for establishing new connections */
	private final int port;
	/** responsible for creating and binding the server socket */
	private final SocketFactory socketFactory;
	/** handles newly established connections */
	private final Consumer<Socket> newConnectionHandler;
	/** a thread pool used to handle incoming connections */
	private final ExecutorService incomingConnPool;

	public ConnectionServer(
			final byte[] ip,
			final int port,
			final SocketFactory socketFactory,
			final Consumer<Socket> newConnectionHandler) {
		this.ip = (ip != null) ? ip : LISTEN_IP;
		this.port = port;
		this.newConnectionHandler = newConnectionHandler;
		this.socketFactory = socketFactory;
		this.incomingConnPool = Executors.newCachedThreadPool(
				new ThreadConfiguration()
						.setThreadName("sync_server")
						.buildFactory()
		);

	}

	@Override
	public void run() throws InterruptedException {
		try (ServerSocket serverSocket = socketFactory.createServerSocket(ip, port)) {
			listen(serverSocket);
		} catch (final RuntimeException | IOException e) {
			LOG.error(EXCEPTION.getMarker(), "Cannot bind ServerSocket", e);
		}
		// if the above fails, sleep a while before trying again
		Thread.sleep(SLEEP_AFTER_BIND_FAILED_MS);
	}

	/**
	 * listens for incoming connections until interrupted or socket is closed
	 */
	private void listen(final ServerSocket serverSocket) throws InterruptedException {
		// Handle incoming connections
		while (!serverSocket.isClosed()) {
			try {
				final Socket clientSocket = serverSocket.accept(); // listen, waiting until someone connects
				incomingConnPool.submit(() -> newConnectionHandler.accept(clientSocket));
			} catch (final SocketTimeoutException expectedWithNonZeroSOTimeout) {
				// A timeout is expected, so we won't log it
				if (Thread.currentThread().isInterrupted()) {
					// since accept() cannot be interrupted, we check the interrupted status on a timeout and throw
					throw new InterruptedException();
				}
			} catch (final RuntimeException | IOException e) {
				LOG.error(EXCEPTION.getMarker(), "SyncServer serverSocket.accept() error", e);
			}
		}
	}
}
