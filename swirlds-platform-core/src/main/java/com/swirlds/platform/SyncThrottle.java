/*
 * (c) 2016-2021 Swirlds, Inc.
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

import com.swirlds.platform.sync.SyncOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Random;

import static com.swirlds.logging.LogMarker.SYNC_THROTTLE;

/**
 * A type to determine whether to slow down the sync, and if so, by how much. The purpose
 * is to mitigate either node falling behind the peer. The current
 * implementation determines a number of extra bytes to write into a {@link SyncOutputStream}
 * based upon a threshold {@link Settings} value {@link Settings#throttle7maxBytes}
 */
class SyncThrottle {
	/**
	 * use this for all logging, as controlled by the optional data/log4j2.xml file
	 */
	private static final Logger log = LogManager.getLogger();

	/**
	 * The minimum number of received events to prevent writing extra bytes
	 */
	private final long fallingBehindThreshold;

	/**
	 * The number of bytes written to the {#link SyncOutputStream} when throttling is enabled.
	 * Set in {@link SyncThrottle#initialize};
	 */
	private long syncByteCountAtStart;

	/**
	 * Constructor
	 *
	 * @param numberOfNodes
	 * 		the number of nodes in the network
	 */
	protected SyncThrottle(final long numberOfNodes) {
		this.fallingBehindThreshold = (long) (numberOfNodes * Settings.throttle7threshold);
		this.syncByteCountAtStart = 0;
	}

	/**
	 * Call this function to initialize the throttle.
	 *
	 * @param conn
	 * 		the {@link SyncConnection} object to which this throttle will be applied.
	 */
	protected void initialize(final SyncConnection conn) {
		// count of bytes written here, used to slow down when not falling behind
		syncByteCountAtStart = conn.getDos().getSyncByteCounter().getCount();
	}

	/**
	 * Compute and write a number of bytes into a {@link SyncOutputStream} referenced by a {@link SyncConnection}
	 *
	 * @param conn
	 * 		the {@link SyncConnection} object to which this throttle will be applied
	 * @param nEventsReceived
	 * 		the number of events received
	 * @return the number of bytes written
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} referenced by {@code conn} throws
	 */
	protected int apply(final SyncConnection conn, final int nEventsReceived) throws IOException {

		// If this node is not falling behind the other node,
		// then write bytes to mitigate falling behind the other node.
		if (nEventsReceived < fallingBehindThreshold) {
			final long bytesSent = conn.getDos().getSyncByteCounter().getCount() - syncByteCountAtStart;

			final int nThrottleBytes = getNumThrottleBytes((int) bytesSent);

			writeRandomBytes(conn, nThrottleBytes);

			return nThrottleBytes;
		}

		// If this node is falling behind the other node, write nothing: no throttling
		else {
			conn.getDos().writeByteArray(new byte[0], true);
			return 0;
		}
	}

	/**
	 * Compute the number of bytes to write. This function is the entire dependency of this type on settings values in
	 * {@link Settings}
	 *
	 * @param bytesSent
	 * 		the number of bytes sent before extra bytes are to be written
	 * @return the number of extra bytes to write
	 */
	private static int getNumThrottleBytes(final int bytesSent) {
		int nThrottleBytes = (int) (1 + bytesSent * Settings.throttle7extra);

		if (nThrottleBytes > Settings.throttle7maxBytes) {
			nThrottleBytes = Settings.throttle7maxBytes;
		}

		return nThrottleBytes;
	}

	/**
	 * Unconditionally write a number of bytes, each with a pseudo-random value, into a {@link SyncOutputStream}
	 * referenced by a {@link SyncConnection}
	 *
	 * @param conn
	 * 		the {@link SyncConnection} object to which this throttle will be applied
	 * @param nThrottleBytes
	 * 		the number of bytes to write
	 * @throws IOException
	 * 		iff the {@link SyncOutputStream} referenced by {@code conn} throws
	 */
	private static void writeRandomBytes(final SyncConnection conn, final int nThrottleBytes) throws IOException {
		final byte[] randomBytes = new byte[nThrottleBytes];
		new Random().nextBytes(randomBytes);
		conn.getDos().writeByteArray(randomBytes, true);

		log.debug(
				SYNC_THROTTLE.getMarker(), "{}wrote {} bytes to slow down",
				() -> " `SyncThrottle.apply`: ",
				() -> nThrottleBytes);
	}

}
