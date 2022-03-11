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
package com.swirlds.platform;

import com.swirlds.common.NodeId;
import com.swirlds.platform.internal.PlatformThreadFactory;
import com.swirlds.platform.sync.SyncConstants;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.logging.LogMarker.SYNC_START;

/**
 * Run this class as a separate thread. It will repeatedly wait() on the given port, and put the stream of
 * anyone who connects into the listenerConn array. The get methods return the stream for a given ID, or
 * null if it doesn't exist or is closed already.
 */
class SyncServer implements Runnable {
	/** total number of connections created so far (both caller and listener) */
	final AtomicInteger connsCreated = new AtomicInteger(0);
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** overrides ip if not null */
	private static final byte[] listenIP = new byte[] { 0, 0, 0, 0 };
	/** the Platform running this SyncServer server */
	private final AbstractPlatform platform;
	/** the IP address that this server listens on for establishing new connections */
	private final byte[] ip;
	/** the port that this server listens on for establishing new connections */
	private final int port;
	/** listenerConn.get(i) is the connection used by listener thread i for syncs initiated by member i */
	private final AtomicReferenceArray<SocketSyncConnection> listenerConn;
	/** number of listener threads currently in a sync */
	final AtomicInteger numListenerSyncs = new AtomicInteger(0);
	/** number of syncs currently happening (both caller and listener) */
	final AtomicInteger numSyncs = new AtomicInteger(0);

	/** lock per each other member, each one is used by all caller threads and the heartbeat thread */
	private final List<ReentrantLock> lockCallHeartbeat;
	/** lock per each other member, each one is used by all caller threads and the listener thread */
	private final List<ReentrantLock> lockCallListen;
	/** a thread pool used to handle incoming connections */
	private final ExecutorService incomingConnPool;

	/**
	 * The constructor must be given what ip and port to listen to. It will then listen forever, creating a
	 * DataInputStream and DataOutputStream for each incoming call. The caller must send their name, and the
	 * two associated streams will be associated with the ID in the address book corresponding to their
	 * name.
	 *
	 * @param platform
	 * 		the platform that is using this
	 * @param ip
	 * 		The IP address to listen to. Will listen to "listenIp" instead, if it's not null
	 * @param port
	 * 		the port to listen to
	 */
	SyncServer(final AbstractPlatform platform, final byte[] ip, final int port) {
		this.platform = platform;
		this.ip = (ip != null) ? ip : listenIP;
		this.port = port;
		/* the number of members in the address book. Many arrays etc are this size */
		int numMembers = platform.getNumMembers();
		this.listenerConn = new AtomicReferenceArray<>(numMembers);
		// fair locks have not shown good results
		this.lockCallHeartbeat = Stream.generate(() -> new ReentrantLock(false)).limit(numMembers).toList();
		this.lockCallListen = Stream.generate(() -> new ReentrantLock(false)).limit(numMembers).toList();
		this.incomingConnPool = Executors.newCachedThreadPool(
				new PlatformThreadFactory("sync_server_"));
	}

	/**
	 * Return the total number of bytes written to all the listener's connections since the last time this
	 * was called.
	 *
	 * @return the number of bytes written
	 */
	long getBytesWrittenSinceLast() {
		return SocketSyncConnection.getBytesWrittenSinceLast(platform, listenerConn);
	}

	/**
	 * Returns the DataInputStream from the member with the given ID number, or null if no connection with
	 * that member has been made.
	 *
	 * @param otherId
	 * 		the ID number of the member
	 * @return the stream
	 */
	public SocketSyncConnection getListenerConn(final NodeId otherId) {
		checkConnected(otherId);// this currently DOESN'T recreate a missing connection, but does set it to
		// null
		return listenerConn.get(otherId.getIdAsInt());// otherId assumed to be main
	}

	/**
	 * Is this a valid, operational connection? If not, disconnect and erase it from the arrays.
	 *
	 * @param otherId
	 * 		ID number of this member
	 */
	boolean checkConnected(final NodeId otherId) {
		SocketSyncConnection conn = null;
		try {
			conn = listenerConn.get(otherId.getIdAsInt()); // otherId assumed to be main
			if (conn == null) {
				return false;
			}
			final Socket s = conn.getSocket();
			if (s != null && !s.isClosed() && s.isBound() && s.isConnected()) {
				return true; // this is a good connection, so use it
			}
		} catch (final Exception e) {
			log.error(EXCEPTION.getMarker(), "SyncServer.checkConnection error", e);
		}
		// if anything is wrong with it, forget it and return false
		if (conn != null) {
			conn.disconnect(false, 10);
		}
		return false;
	}

	/**
	 * call this in its own thread, and it will run forever, accepting incoming calls.
	 */
	@SuppressWarnings("resource") // createServerSocket creates a socket that isn't closed.
	public void run() {
		ServerSocket serverSocket = null;
		try {
			// keep trying to connect to port on this computer, until success
			while (serverSocket == null || !serverSocket.isBound()) {
				try {
					serverSocket = platform.getSocketFactory().createServerSocket(ip, port);
				} catch (final IOException e) {
					log.error(EXCEPTION.getMarker(), "SyncServer.run error", e);

					try {
						Thread.sleep(Settings.sleepSyncServerSocketBind);
						platform.getStats().sleep3perSecond.cycle();
					} catch (final InterruptedException ex) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}

			// Handle incoming connections
			while (true) {
				Socket clientSocket = null;
				try {
					serverSocket
							.setSoTimeout(Settings.timeoutServerAcceptConnect);
					clientSocket = serverSocket.accept(); // listen, waiting until someone connects
					incomingConnPool.submit(new IncomingConnectionHandler(this,
							clientSocket, platform));
				} catch (final SocketTimeoutException expectedWithNonZeroSOTimeout) {
					// Suppress the timeout conditions
				} catch (final Exception e) {
					log.error(EXCEPTION.getMarker(), "SyncServer serverSocket.accept() error", e);
					try {
						if (clientSocket != null) {
							clientSocket.close();
						}
					} catch (final Exception ignored) {
						// Suppress any exceptions during cleanup
					}
				}
			}
		} finally {
			try {
				if (serverSocket != null) {
					serverSocket.close();
				}
			} catch (final Exception ignored) {
				// Suppress any exceptions during cleanup
			}
		}
	}

	/**
	 * This method is called by the thread from the pool when the TCP connection has been established
	 *
	 * @param socket
	 * 		the socket of the TCP connection that has been established
	 * @param otherId
	 * 		ID number of the remote member
	 */
	void tcpConnectionEstablished(final Socket socket, final NodeId otherId) {
		SyncInputStream dis = null;
		SyncOutputStream dos = null;
		try {
			dis = SyncInputStream.createSyncInputStream(socket.getInputStream(), Settings.bufferSize);
			dos = SyncOutputStream.createSyncOutputStream(socket.getOutputStream(), Settings.bufferSize);

			final SocketSyncConnection sc = new SocketSyncConnection(
					platform.getSelfId(),
					otherId,
					platform,
					false,
					socket,
					dis,
					dos
			);
			final SocketSyncConnection oldConn = listenerConn.get(otherId.getIdAsInt());
			// end any old connection that might exist with them
			if (oldConn != null) {
				log.error(SOCKET_EXCEPTIONS.getMarker(),
						"{} got new connection from {}, disconnecting old one",
						sc.getSelfId(), sc.getOtherId());
				oldConn.disconnect(false, 11);
			}
			listenerConn.set(otherId.getIdAsInt(), sc); // remember the new connection
			platform.getSyncServer().connsCreated.incrementAndGet(); // count new connections
			log.debug(SYNC_START.getMarker(),
					"{} accepted connection from {}", sc.getSelfId(),
					sc.getOtherId());

		} catch (final IOException e) {
			log.error(EXCEPTION.getMarker(), "", e);
			close(dis, dos, socket);
		}
	}

	private void close(final DataInputStream dis, final DataOutputStream dos,
			final Socket clientSocket) {
		try {
			if (dis != null) {
				dis.close();
			}
		} catch (final Exception ignored) {
		}

		try {
			if (dos != null) {
				dos.close();
			}
		} catch (final Exception ignored) {
		}

		try {
			if (clientSocket != null) {
				clientSocket.close();
			}
		} catch (final Exception ignored) {
		}
	}

	/**
	 * This class is used to accept incoming connections in a separate thread
	 */
	private class IncomingConnectionHandler implements Runnable {
		private final SyncServer syncServer;
		private final Socket clientSocket;
		private final AbstractPlatform platform;

		/**
		 * @param syncServer
		 * 		reference to SyncServer to call a method when the connection has been established
		 * @param clientSocket
		 * 		the socket that has been newly opened
		 * @param platform
		 * 		reference to the Platform
		 */
		public IncomingConnectionHandler(final SyncServer syncServer,
				final Socket clientSocket, final AbstractPlatform platform) {
			super();
			this.syncServer = syncServer;
			this.clientSocket = clientSocket;
			this.platform = platform;
		}

		@Override
		public void run() {
			DataInputStream dis = null;
			DataOutputStream dos = null;
			long otherId = -1;
			long acceptTime = 0;
			try {
				acceptTime = System.currentTimeMillis();
				clientSocket.setTcpNoDelay(Settings.tcpNoDelay);
				clientSocket.setSoTimeout(Settings.timeoutSyncClientSocket);
				dis = new DataInputStream(clientSocket.getInputStream());

				dos = new DataOutputStream(clientSocket.getOutputStream());

				final String otherKey = dis.readUTF();

				otherId = platform.getAddressBook().getId(otherKey);

				dos.writeInt(SyncConstants.COMM_CONNECT);// send an ACK for creating connection
				dos.flush();

				// ignore invalid IDs, but store the streams for valid ones
				if (otherId >= 0
						&& otherId < platform.getAddressBook()
						.getSize()
						&& platform.getConnectionGraph().isAdjacent(
						platform.getSelfId().getIdAsInt(), (int) otherId)) {
					log.debug(SYNC_START.getMarker(),
							"listener {} just established connection initiated by {}",
							platform.getSelfId(), otherId);

					syncServer.tcpConnectionEstablished(clientSocket, NodeId.createMain(otherId));
				} else {
					close(dis, dos, clientSocket);
				}
			} catch (final SocketTimeoutException expectedWithNonZeroSOTimeout) {
				// Suppressed intentionally
			} catch (final SSLException e) {
				// log the SSL connection exception which is caused by socket exceptions as warning.
				log.warn(SOCKET_EXCEPTIONS.getMarker(),
						"Listener {} hearing {} had SSLException:", platform.getSelfId(),
						otherId, e);
				close(dis, dos, clientSocket);
			} catch (final Exception e) {
				log.error(EXCEPTION.getMarker(),
						"SyncConnection.acceptConnection() error, remote IP: {}\nTime from accept to exception: {} " +
								"ms",
						clientSocket.getInetAddress().toString(),
						acceptTime == 0 ? "N/A" : (System.currentTimeMillis() - acceptTime), e);
				log.error(SYNC.getMarker(),
						"Listener {} hearing {} had general Exception:", platform.getSelfId(), otherId, e);
				close(dis, dos, clientSocket);
			}
		}
	}

	/**
	 * Returns a lock that ensures we don't have multiple ongoing syncs with the same node
	 *
	 * @param nodeId
	 * 		the ID of the node whose lock we need
	 * @return the sync ongoing lock for this node
	 */
	public ReentrantLock getSyncOngoingLock(final int nodeId) {
		return lockCallListen.get(nodeId);
	}

	/**
	 * Same as {@link SyncServer#getOutboundConnLock(NodeId)} but using an int as the ID
	 */
	public ReentrantLock getOutboundConnLock(final int nodeId) {
		return lockCallHeartbeat.get(nodeId);
	}

	/**
	 * Returns a lock that ensures only one thread uses the connection at a time
	 *
	 * @param nodeId
	 * 		the ID of the node whose lock we need
	 * @return the connection lock for this node
	 */
	public ReentrantLock getOutboundConnLock(final NodeId nodeId) {
		return getOutboundConnLock(nodeId.getIdAsInt());
	}

	/**
	 * @return A {@link Collection} of all sync ongoing locks
	 */
	public Collection<ReentrantLock> getSyncOngoingLocks() {
		return lockCallListen;
	}
}
