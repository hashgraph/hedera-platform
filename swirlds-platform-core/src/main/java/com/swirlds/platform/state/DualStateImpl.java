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

package com.swirlds.platform.state;

import com.swirlds.common.SwirldDualState;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.logging.payloads.SetFreezeTimePayload;
import com.swirlds.logging.payloads.SetLastFrozenTimePayload;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;

import static com.swirlds.logging.LogMarker.FREEZE;

/**
 * Contains any data that is either read or written by the platform and the application
 */
public class DualStateImpl extends AbstractMerkleLeaf implements PlatformDualState, SwirldDualState {
	private static final Logger LOGGER = LogManager.getLogger();

	public static final long CLASS_ID = 0x565e2e04ce3782b8L;

	private static final class ClassVersion {
		private static final int ORIGINAL = 1;
	}

	/** the time when the freeze starts */
	private volatile Instant freezeTime;

	/** the last freezeTime based on which the nodes were frozen */
	private volatile Instant lastFrozenTime;

	public DualStateImpl() {
	}

	private DualStateImpl(final DualStateImpl that) {
		super(that);
		this.freezeTime = that.freezeTime;
		this.lastFrozenTime = that.lastFrozenTime;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInstant(freezeTime);
		out.writeInstant(lastFrozenTime);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		freezeTime = in.readInstant();
		lastFrozenTime = in.readInstant();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setFreezeTime(Instant freezeTime) {
		this.freezeTime = freezeTime;
		LOGGER.info(FREEZE.getMarker(), "setFreezeTime: {}", () -> freezeTime);
		LOGGER.info(FREEZE.getMarker(), () -> new SetFreezeTimePayload(freezeTime).toString());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Instant getFreezeTime() {
		return freezeTime;
	}

	protected void setLastFrozenTime(Instant lastFrozenTime) {
		this.lastFrozenTime = lastFrozenTime;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setLastFrozenTimeToBeCurrentFreezeTime() {
		this.lastFrozenTime = freezeTime;
		LOGGER.info(FREEZE.getMarker(), "setLastFrozenTimeToBeCurrentFreezeTime: {}", () -> lastFrozenTime);
		LOGGER.info(FREEZE.getMarker(), () -> new SetLastFrozenTimePayload(freezeTime).toString());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Instant getLastFrozenTime() {
		return lastFrozenTime;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isDataExternal() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DualStateImpl copy() {
		return new DualStateImpl(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		DualStateImpl that = (DualStateImpl) o;

		return new EqualsBuilder()
				.append(freezeTime, that.freezeTime)
				.append(lastFrozenTime, that.lastFrozenTime)
				.isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(freezeTime)
				.append(lastFrozenTime)
				.toHashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
				.append("freezeTime", freezeTime)
				.append("lastFrozenTime", lastFrozenTime)
				.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isInFreezePeriod(Instant consensusTime) {
		// if freezeTime is not set, or consensusTime is before freezeTime, we are not in a freeze period
		// if lastFrozenTime is equal to or after freezeTime, which means the nodes have been frozen once at/after the
		// freezeTime, we are not in a freeze period
		if (freezeTime == null || consensusTime.isBefore(freezeTime)) {
			return false;
		}
		// Now we should check whether the nodes have been frozen at the freezeTime.
		// when consensusTime is equal to or after freezeTime,
		// and lastFrozenTime is before freezeTime, we are in a freeze period.
		return lastFrozenTime == null || lastFrozenTime.isBefore(freezeTime);
	}
}
