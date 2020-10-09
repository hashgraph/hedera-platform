/*
 * (c) 2016-2020 Swirlds, Inc.
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

import com.swirlds.common.AddressBook;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.DataStreamUtils;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.platform.Utilities;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** all the known signatures for a particular signed state */
@ConstructableIgnored /* has to be ignored, doesnt work with a no-args constructor at the moment */
public class SigSet implements FastCopyable<SigSet>, SelfSerializable {
	private static final long CLASS_ID = 0x756d0ee945226a92L;
	private static final int VERSION_ORIGINAL = 1;
	private static final int VERSION_MIGRATE_TO_SERIALIZABLE = 2;
	private static final int CLASS_VERSION = VERSION_MIGRATE_TO_SERIALIZABLE;

	/** the number of signatures collected */
	private volatile int count;
	/** total stake of all members whose signatures have been collected so far */
	private volatile long stakeCollected = 0;
	/** the number of members, each of which can sign */
	private int numMembers;
	/** have members with more than 2/3 of the total stake signed? */
	private volatile boolean complete = false;
	/** array element i is the signature for the member with ID i */
	private AtomicReferenceArray<SigInfo> sigInfos;
	/** the address book in force at the time of this state */
	private AddressBook addressBook;

	/**
	 * False until complete and has been processed as a newly completed SigSet
	 */
	private boolean markedAsNewlyComplete = false;

	private boolean immutable;

	/** get array where element i is the signature for the member with ID i */
	SigInfo[] getSigInfosCopy() {
		SigInfo a[] = new SigInfo[numMembers];
		for (int i = 0; i < a.length; i++) {
			a[i] = sigInfos.get(i);
		}
		return a;
	}

	private SigSet(final SigSet sourceSigSet) {
		this.count = sourceSigSet.getCount();
		this.stakeCollected = sourceSigSet.getStakeCollected();
		this.numMembers = sourceSigSet.getNumMembers();
		this.complete = sourceSigSet.isComplete();
		this.sigInfos = sourceSigSet.sigInfos;
		this.addressBook = sourceSigSet.getAddressBook().copy();
	}

	/** create the signature set, taking the address book at this moment as the population */
	public SigSet(AddressBook addressBook) {
		this.addressBook = addressBook;
		this.numMembers = addressBook.getSize();
		sigInfos = new AtomicReferenceArray<>(numMembers);
		count = 0;
		stakeCollected = 0;
	}

	/**
	 * Register a new signature for one member. If this member already has a signed state here, then the new
	 * one that was passed in is ignored.
	 */
	public synchronized void addSigInfo(SigInfo sigInfo) {
		int id = (int) sigInfo.getMemberId();
		if (sigInfos.get(id) != null) {
			return; // ignore if we already have a signature for this member
		}
		sigInfos.set(id, sigInfo);
		count++;
		stakeCollected += addressBook.getStake(id);
		calculateComplete();
	}

	private void calculateComplete() {
		complete = Utilities.isStrongMinority(stakeCollected, addressBook.getTotalStake());
	}

	/**
	 * Get the SigInfo for the given ID
	 *
	 * @param memberId
	 * 		the ID of the member
	 * @return returns the SigInfo object or null if we don't have this members sig
	 */
	public SigInfo getSigInfo(int memberId) {
		return sigInfos.get(memberId);
	}

	/** does this contain signatures from members with at least 1/3 of the total stake? */
	public boolean isComplete() {
		return complete;
	}

	/**
	 * Once this SigSet is complete, this function will return true the first time it is called
	 * and false afterwards. Used to handle SigSets that have just become complete.
	 */
	public boolean isNewlyComplete() {
		if (complete && !markedAsNewlyComplete) {
			markedAsNewlyComplete = true;
			return true;
		}
		return false;
	}

	/**
	 * @return true if this set has signatures from all the members, false otherwise
	 */
	boolean hasAllSigs() {
		if (SettingsCommon.enableBetaMirror) {
			// If beta mirror node logic is enabled then we should only consider nodes with stake in this validation
			// However, since mirror nodes themselves will still create their own signature in addition to the nodes
			// with actual stake we must also allow (numWithStake + 1) as a valid condition
			return count == addressBook.getNumberWithStake() || count == addressBook.getNumberWithStake() + 1;
		} else {
			return count == numMembers;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SigSet copy() {
		throwIfImmutable();
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(numMembers);
		out.writeSerializableArray(getSigInfosCopy(), false, true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		numMembers = in.readInt();
		SigInfo[] sigInfoArr = in.readSerializableArray(
				SigInfo[]::new,
				addressBook.getSize(),
				false,
				SigInfo::new);
		processDeserializedSigInfoArray(sigInfoArr);
	}

	/**
	 * Read this SigSEt from a stream. The addressBook must already be loaded before calling this.
	 *
	 * @param inStream
	 * 		the stream to read from
	 * @throws IOException
	 */
	@Override
	public void copyFrom(SerializableDataInputStream inStream) throws IOException {
		DataStreamUtils.readValidLong(inStream, "version", VERSION_ORIGINAL);
		numMembers = inStream.readInt();
		SigInfo sigInfoArr[] = new SigInfo[numMembers];
		try {
			sigInfoArr = Utilities.readFastCopyableArray(inStream,
					SigInfo.class);
		} catch (Exception e) {
			e.printStackTrace();
		}

		processDeserializedSigInfoArray(sigInfoArr);
		calculateComplete();
	}

	private void processDeserializedSigInfoArray(SigInfo[] sigInfoArr) {
		count = 0;
		stakeCollected = 0;
		sigInfos = new AtomicReferenceArray<>(numMembers);
		for (int id = 0; id < sigInfoArr.length; id++) {
			if (sigInfoArr[id] != null) {
				sigInfos.set(id, sigInfoArr[id]);
				count++;
				stakeCollected += addressBook.getStake(id);
			}
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void release() {
	}

	/**
	 * getter for the number of signatures collected
	 *
	 * @return number of signatures collected
	 */
	public int getCount() {
		return count;
	}

	/**
	 * getter for total stake of all members whose signatures have been collected so far
	 *
	 * @return total stake of members whose signatures have been collected
	 */
	public long getStakeCollected() {
		return stakeCollected;
	}

	/**
	 * getter for the number of members, each of which can sign
	 *
	 * @return number of members
	 */
	public int getNumMembers() {
		return numMembers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyFromExtra(SerializableDataInputStream inStream) throws IOException {
	}

	public AddressBook getAddressBook() {
		return addressBook;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		SigSet sigSet = (SigSet) o;

		if (sigInfos.length() != sigSet.sigInfos.length()) {
			return false;
		}
		for (int i = 0; i < sigInfos.length(); i++) {
			if (!Objects.equals(sigInfos.get(i), sigSet.sigInfos.get(i))) {
				return false;
			}
		}

		return new EqualsBuilder()
				.append(count, sigSet.count)
				.append(stakeCollected, sigSet.stakeCollected)
				.append(numMembers, sigSet.numMembers)
				.append(complete, sigSet.complete)
				//.append(sigInfos, sigSet.sigInfos) atomic array does not implement equals()
				.append(addressBook, sigSet.addressBook)
				.isEquals();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(count)
				.append(stakeCollected)
				.append(numMembers)
				.append(complete)
				.append(sigInfos)
				.append(addressBook)
				.toHashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
				.append("count", count)
				.append("stakeCollected", stakeCollected)
				.append("numMembers", numMembers)
				.append("complete", complete)
				.append("sigInfos", sigInfos)
				.toString();
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
		return CLASS_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isImmutable() {
		return this.immutable;
	}
}


