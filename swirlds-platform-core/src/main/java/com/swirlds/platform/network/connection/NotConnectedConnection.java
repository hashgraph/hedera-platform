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

package com.swirlds.platform.network.connection;

import com.swirlds.common.NodeId;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;

import java.net.SocketException;

/**
 * An implementation of {@link SyncConnection} that is used to avoid returning null if there is no connection.
 * This connection will never be connected and will do nothing on disconnect. All other methods will throw an
 * exception.
 */
public class NotConnectedConnection implements SyncConnection {
	private static final SyncConnection SINGLETON = new NotConnectedConnection();
	private static final UnsupportedOperationException NOT_IMPLEMENTED =
			new UnsupportedOperationException("Not implemented");

	public static SyncConnection getSingleton() {
		return SINGLETON;
	}

	/**
	 * Does nothing since its not a real connection
	 */
	@Override
	public void disconnect() {
		// nothing to do
	}

	/**
	 * Throws an {@link UnsupportedOperationException} since this is not a real connection
	 *
	 * @return never returns, always throws
	 */
	@Override
	public NodeId getSelfId() {
		throw NOT_IMPLEMENTED;
	}

	/**
	 * Throws an {@link UnsupportedOperationException} since this is not a real connection
	 *
	 * @return never returns, always throws
	 */
	@Override
	public NodeId getOtherId() {
		throw NOT_IMPLEMENTED;
	}

	/**
	 * Throws an {@link UnsupportedOperationException} since this is not a real connection
	 *
	 * @return never returns, always throws
	 */
	@Override
	public SyncInputStream getDis() {
		throw NOT_IMPLEMENTED;
	}

	/**
	 * Throws an {@link UnsupportedOperationException} since this is not a real connection
	 *
	 * @return never returns, always throws
	 */
	@Override
	public SyncOutputStream getDos() {
		throw NOT_IMPLEMENTED;
	}

	/**
	 * @return always returns false
	 */
	@Override
	public boolean connected() {
		return false;
	}

	/**
	 * Throws an {@link UnsupportedOperationException} since this is not a real connection
	 *
	 * @return never returns, always throws
	 */
	@Override
	public int getTimeout() throws SocketException {
		throw NOT_IMPLEMENTED;
	}

	/**
	 * Throws an {@link UnsupportedOperationException} since this is not a real connection
	 */
	@Override
	public void setTimeout(int timeoutMillis) throws SocketException {
		throw NOT_IMPLEMENTED;
	}

	/**
	 * Throws an {@link UnsupportedOperationException} since this is not a real connection
	 */
	@Override
	public void initForSync() {
		throw NOT_IMPLEMENTED;
	}

	/**
	 * Throws an {@link UnsupportedOperationException} since this is not a real connection
	 *
	 * @return never returns, always throws
	 */
	@Override
	public boolean isOutbound() {
		throw NOT_IMPLEMENTED;
	}

	@Override
	public String getDescription() {
		return "NotConnectedConnection";
	}
}
