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
package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.Event;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * A shadow event wraps a hashgraph event, and provides parent and child pointers to shadow events.
 * This is the elemental type of a {@link ShadowGraph}. Connection to and disconnection from
 * other events in a shadow graph is implemented here.
 */
public class ShadowEvent {
	/**
	 * the real event
	 */
	private final Event event;

	/**
	 * Self-children. Every shadow event whose self-parent is this one.
	 */
	private final List<ShadowEvent> selfChildren;

	/**
	 * Other-children. Every shadow event whose other-parent is this one.
	 */
	private final List<ShadowEvent> otherChildren;

	/**
	 * self-parent
	 */
	private ShadowEvent selfParent;

	/**
	 * other-parent
	 */
	private ShadowEvent otherParent;

	/**
	 * Construct a shadow event from an event and the shadow events of its parents
	 *
	 * @param event
	 * 		the event
	 * @param selfParent
	 * 		the self-parent event's shadow
	 * @param otherParent
	 * 		the other-parent event's shadow
	 */
	public ShadowEvent(final Event event, final ShadowEvent selfParent, final ShadowEvent otherParent) {
		this(event);
		setSelfParent(selfParent);
		setOtherParent(otherParent);
	}

	/**
	 * Construct a shadow event from an event
	 *
	 * @param event
	 * 		the event
	 */
	public ShadowEvent(final Event event) {
		this.event = event;
		this.selfParent = null;
		this.otherParent = null;
		this.selfChildren = new ArrayList<>();
		this.otherChildren = new ArrayList<>();
	}

	/**
	 * Get the self-parent of {@code this} shadow event
	 *
	 * @return the self-parent of {@code this} shadow event
	 */
	public ShadowEvent getSelfParent() {
		return this.selfParent;
	}

	/**
	 * Set the self-parent of {@code this} shadow event
	 *
	 * @param s
	 * 		the self-parent
	 */
	private void setSelfParent(final ShadowEvent s) {
		if (s != null) {
			selfParent = s;

			if (!s.selfChildren.contains(this)) {
				s.selfChildren.add(this);
			}
		}
	}

	/**
	 * Get the other-parent of {@code this} shadow event
	 *
	 * @return the other-parent of {@code this} shadow event
	 */
	public ShadowEvent getOtherParent() {
		return this.otherParent;
	}

	/**
	 * Set the other-parent of {@code this} shadow event
	 *
	 * @param s
	 * 		the other-parent of {@code this} shadow event
	 */
	private void setOtherParent(final ShadowEvent s) {
		if (s != null) {
			otherParent = s;

			if (!s.otherChildren.contains(this)) {
				s.otherChildren.add(this);
			}
		}
	}

	/**
	 * Get the self-children of {@code this} shadow event
	 *
	 * @return the self-children of {@code this} shadow event
	 */
	public List<ShadowEvent> getSelfChildren() {
		return selfChildren;
	}

	/**
	 * Get the other-children of {@code this} shadow event
	 *
	 * @return the other-children of {@code this} shadow event
	 */
	public List<ShadowEvent> getOtherChildren() {
		return otherChildren;
	}

	/**
	 * Get the number of self-children of {@code this} shadow event
	 *
	 * @return the number of self-children of {@code this} shadow event
	 */
	public int getNumSelfChildren() {
		return selfChildren.size();
	}

	/**
	 * Get the number of other-children of {@code this} shadow event
	 *
	 * @return the number of other-children of {@code this} shadow event
	 */
	public int getNumOtherChildren() {
		return otherChildren.size();
	}

	/**
	 * Get the hashgraph event references by this shadow event
	 *
	 * @return the hashgraph event references by this shadow event
	 */
	public Event getEvent() {
		return event;
	}

	public EventImpl getEventImpl() {
		return (EventImpl) event;
	}

	/**
	 * The cryptographic hash of an event shadow is the cryptographic hash of the event base
	 *
	 * @return The cryptographic base hash of an event.
	 */
	public Hash getEventBaseHash() {
		return event.getBaseHash();
	}

	/**
	 * Evaluates to {@code true} iff {@code this} shadow event is a tip event
	 *
	 * @return {@code true} iff {@code this} shadow event is a tip event
	 */
	public boolean isTip() {
		return selfChildren.isEmpty();
	}

	/**
	 * Disconnect this shadow event from its parents and children. Remove inbound links and outbound links
	 */
	public void disconnect() {
		removeSelfParent();
		removeOtherParent();

		for (final ShadowEvent s : selfChildren) {
			s.selfParent = null;
		}

		for (final ShadowEvent s : otherChildren) {
			s.otherParent = null;
		}

		selfChildren.clear();
		otherChildren.clear();
	}

	/**
	 * Add a self-child.
	 *
	 * @param s
	 * 		the self child
	 */
	public void addSelfChild(final ShadowEvent s) {
		if (s != null) {
			s.setSelfParent(this);
		}
	}

	/**
	 * Add an other-child
	 *
	 * @param s
	 * 		the other-child
	 */
	public void addOtherChild(final ShadowEvent s) {
		if (s != null) {
			s.setOtherParent(this);
		}
	}

	private void removeSelfParent() {
		if (selfParent != null) {
			selfParent.selfChildren.remove(this);
			selfParent = null;
		}
	}

	private void removeOtherParent() {
		if (otherParent != null) {
			otherParent.otherChildren.remove(this);
			otherParent = null;
		}
	}

	/**
	 * Two shadow events are equal iff their reference hashgraph events are equal.
	 *
	 * @param o
	 * @return true iff {@code this} and {@code o} reference hashgraph events that compare equal
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ShadowEvent)) {
			return false;
		}

		final ShadowEvent s = (ShadowEvent) o;

		return getEventBaseHash().equals(s.getEventBaseHash());
	}

	/**
	 * The hash code of a shadow event is the Swirlds cryptographic base hash of the hashgraph event which this shadow
	 * event references.
	 *
	 * @return the hash code
	 */
	@Override
	public int hashCode() {
		return getEventBaseHash().hashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("{");

		builder.append(EventStrings.toShortString((EventImpl) getEvent()));

		// Work around a bug in EventStringBuilder
		if(getSelfParent() != null) {
			builder.append(" sp").append(EventStrings.toShortString((EventImpl) getSelfParent().getEvent()));
		} else {
			builder.append(" sp(null)");
		}

		if(getOtherParent() != null) {
			builder.append(" op").append(EventStrings.toShortString((EventImpl) getOtherParent().getEvent()));
		} else {
			builder.append(" op(null)");
		}

		builder.append(", sc:[");

		for (final ShadowEvent sc : getSelfChildren()) {
			builder.append(EventStrings.toShortString((EventImpl) sc.getEvent())).append(", ");
		}

		builder.append("], oc:[");
		for (final ShadowEvent oc : getOtherChildren()) {
			builder.append(EventStrings.toShortString((EventImpl) oc.getEvent())).append(", ");
		}
		builder.append(']').append('}');

		return builder.toString();
	}
}
