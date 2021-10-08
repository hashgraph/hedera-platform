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

import com.swirlds.common.events.Event;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.EventImpl;

/**
 * A type used to verifying the internal integrity of a shadow graph, and to verify
 * that a shadow graph and hashgraph are correctly coordinated.
 * <p>
 * This type is used only for logging, and is rarely used.
 * </p>
 */
public final class ShadowGraphVerification {

	/**
	 * This class is not instantiable
	 */
	private ShadowGraphVerification() {

	}

	/**
	 * Verify the status of a shadow graph, with respect to this nodes consensus hashgraph
	 *
	 * @param sgm
	 * 		the shadow graph manager which references the shadow graph to check
	 * @param consensus
	 * 		this node's consensus object
	 * @return a {@link ShadowGraphVerificationStatus}
	 */
	public static ShadowGraphVerificationStatus verify(
			final ShadowGraphManager sgm,
			final Consensus consensus) {
		return verify(sgm, consensus.getAllEvents());
	}

	/**
	 * Verify the status of a shadow graph, with respect to an array of hashgraph events
	 *
	 * @param sgm
	 * 		the shadow graph manager which references the shadow graph to check
	 * @param events
	 * 		an array of {@link EventImpl}s
	 * @return a {@link ShadowGraphVerificationStatus}
	 */
	public static ShadowGraphVerificationStatus verify(
			final ShadowGraphManager sgm,
			final EventImpl[] events) {

		for (final EventImpl e : events) {
			// yet from the shadow graph.
			if (sgm.expired(e)) {
				continue;
			}

			final ShadowEvent s = sgm.shadow(e);

			final ShadowGraphVerificationStatus status = verify(s, sgm);
			if (status != ShadowGraphVerificationStatus.VERIFIED) {
				return status;
			}
		}

		return ShadowGraphVerificationStatus.VERIFIED;
	}

	/**
	 * Private implementor to verify the status of an individual shadow event.
	 *
	 * @param s
	 * 		a shadow event
	 * @param sgm
	 * 		the shadow graph manager which references the shadow graph to check
	 * @return a {@link ShadowGraphVerificationStatus}
	 */
	private static ShadowGraphVerificationStatus verify(final ShadowEvent s, final ShadowGraphManager sgm) {
		final Event e = s.getEvent();

		// In general, this is not a failure. This node's hashgraph
		// may contain an expired event (with expiration as defined by the
		// hashgraph object) which has not yet been purged by the Hashgraph
		// when this verification was called, but this return value should be
		// transitory in the call sequence.
		if (missingSelfParent(e, s)) {
			return ShadowGraphVerificationStatus.MISSING_SELF_PARENT;
		}

		// In general, this is not a failure. This node's hashgraph
		// may contain an expired event (with expiration as defined by the
		// hashgraph object) which has not yet been purged by the Hashgraph
		// when this verification was called, but this return value should be
		// transitory in the call sequence.
		if (missingOtherParent(e, s)) {
			return ShadowGraphVerificationStatus.MISSING_OTHER_PARENT;
		}

		// The shadow graph should not contain events that the hashgraph no longer contains.
		if (expiredSelfParent(e, s)) {
			return ShadowGraphVerificationStatus.EXPIRED_SELF_PARENT;
		}

		// The shadow graph should not contain events that the hashgraph no longer contains.
		if (expiredOtherParent(e, s)) {
			return ShadowGraphVerificationStatus.EXPIRED_OTHER_PARENT;
		}

		// Internal structural failures: these should never happen.
		if (mismatchedSelfParent(e, s)) {
			return ShadowGraphVerificationStatus.MISMATCHED_SELF_PARENT;
		}

		if (mismatchedOtherParent(e, s)) {
			return ShadowGraphVerificationStatus.MISMATCHED_OTHER_PARENT;
		}

		if (missingSelfChildOfSelfParent(s)) {
			return ShadowGraphVerificationStatus.MISSING_INBOUND_SELF_CHILD_POINTER;
		}

		if (missingOtherChildOfOtherParent(s)) {
			return ShadowGraphVerificationStatus.MISSING_INBOUND_OTHER_CHILD_POINTER;
		}

		if (missingSelfParentOfSelfChild(e, s)) {
			return ShadowGraphVerificationStatus.MISSING_INBOUND_SELF_PARENT_POINTER;
		}

		if (mismatchedSelfParentOfSelfChild(e, s)) {
			return ShadowGraphVerificationStatus.MISMATCHED_INBOUND_SELF_PARENT_POINTER;
		}

		if (missingOtherParentOfOtherChild(e, s)) {
			return ShadowGraphVerificationStatus.MISSING_INBOUND_OTHER_PARENT_POINTER;
		}

		if (mismatchedOtherParentOfOtherChild(e, s)) {
			return ShadowGraphVerificationStatus.MISMATCHED_INBOUND_OTHER_PARENT_POINTER;
		}

		final boolean isSelfParent = s.getNumSelfChildren() != 0;
		final boolean isTip = sgm.getTips().contains(s);

		// Internal failure: tip identification. Should never happen.
		if (isSelfParent && isTip) {
			return ShadowGraphVerificationStatus.INVALID_TIP;
		}

		if (!isSelfParent && !isTip) {
			return ShadowGraphVerificationStatus.MISSING_TIP;
		}

		return ShadowGraphVerificationStatus.VERIFIED;
	}

	/**
	 * Is a hashgraph event that should referenced in the shadow graph, referenced in the shadow graph?
	 * Assumes shadow event {@code s} references hashgraph event {@code e}
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff {@code s} reference {@code e}
	 */
	private static boolean missing(final Event e, final ShadowEvent s) {
		return e != null && s == null;
	}

	/**
	 * Is hashgraph event expired, while its shadow event is still present?
	 * Assumes shadow event {@code s} references hashgraph event {@code e}
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff {@code s} is present while {@code e == null}
	 */
	private static boolean expired(final Event e, final ShadowEvent s) {
		return e == null && s != null;
	}

	/**
	 * Are an event and a shadow event correctly associated?
	 * Assumes shadow event {@code s} references hashgraph event {@code e}
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff {@code s != null} and s reference {@code e}
	 */
	private static boolean mismatched(final Event e, final ShadowEvent s) {
		return s != null && !s.getEvent().equals(e);
	}

	/**
	 * Is the shadow of a hashgraph event's self-parent missing?
	 * Assumes shadow event {@code s} references hashgraph event {@code e}
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff the self-parent hashgraph event of {@code e} is not referenced by the self-parent shadow
	 * 		event of {@code s}
	 */
	private static boolean missingSelfParent(final Event e, final ShadowEvent s) {
		return missing(e.getSelfParent(), s.getSelfParent());
	}

	/**
	 * Does the shadow of a hashgraph event's self-parent equal the self-parent of the hashgraph's shadow event?
	 * Assumes shadow event {@code s} references hashgraph event {@code e}
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff shadow(self-parent({@code e})) == self-parent(shadow({@code e}))
	 */
	private static boolean mismatchedSelfParent(final Event e, final ShadowEvent s) {
		return mismatched(e.getSelfParent(), s.getSelfParent());
	}

	/**
	 * Is the shadow of a hashgraph event's other-parent missing?
	 * Assumes shadow event {@code s} references hashgraph event {@code e}
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff the other-parent hashgraph event of {@code e} is not referenced by the other-parent shadow
	 * 		event of {@code s}
	 */
	private static boolean missingOtherParent(final Event e, final ShadowEvent s) {
		return missing(e.getOtherParent(), s.getOtherParent());
	}

	/**
	 * Is the self-parent of a hashgraph expired, while its shadow is not?
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff the self-parent({@code e}) is expired while shadow(self-parent({@code e})) is not expired
	 */
	private static boolean expiredSelfParent(final Event e, final ShadowEvent s) {
		return expired(e.getSelfParent(), s.getSelfParent());
	}

	/**
	 * Is the other parent of a hashgraph expired, while its shadow is not?
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff the other-parent({@code e}) is expired while shadow(other-parent({@code e})) is not expired
	 */
	private static boolean expiredOtherParent(final Event e, final ShadowEvent s) {
		return expired(e.getOtherParent(), s.getOtherParent());
	}

	/**
	 * Does the shadow of a hashgraph event's other-parent equal the other-parent of the hashgraph's shadow event?
	 * Assumes shadow event {@code s} references hashgraph event {@code e}
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff shadow(other-parent({@code e})) == other-parent(shadow({@code e}))
	 */
	private static boolean mismatchedOtherParent(final Event e, final ShadowEvent s) {
		return mismatched(e.getOtherParent(), s.getOtherParent());
	}

	/**
	 * Does the self-parent of a shadow event have a self-child reference to the shadow event?
	 *
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff self-parent({@code s})) does not reference {@code s} or {@code s} is null
	 */
	private static boolean missingSelfChildOfSelfParent(final ShadowEvent s) {
		return s.getSelfParent() != null && !s.getSelfParent().getSelfChildren().contains(s);
	}

	/**
	 * Does the other-parent of a shadow event have an other-child reference to the shadow event?
	 *
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff other-parent({@code s})) does not reference {@code s} or {@code s} is null
	 */
	private static boolean missingOtherChildOfOtherParent(final ShadowEvent s) {
		return s.getOtherParent() != null && !s.getOtherParent().getOtherChildren().contains(s);
	}

	/**
	 * For a given hashgraph event, do its shadow event's self-children have a self-parent that reference that hashgraph
	 * event?
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff there is a self-child of {@code e} that does not have a self-parent that references {@code e}
	 */
	private static boolean mismatchedSelfParentOfSelfChild(final Event e, final ShadowEvent s) {
		for (final ShadowEvent sc : s.getSelfChildren()) {
			if (mismatched(e, sc.getSelfParent())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * For a given hashgraph event, do its shadow event's self-children have a null self-parent?
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff there is a self-child of {@code e} that has a null self-parent
	 */
	private static boolean missingSelfParentOfSelfChild(final Event e, final ShadowEvent s) {
		for (final ShadowEvent sc : s.getSelfChildren()) {
			if (missing(e, sc.getSelfParent())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * For a given hashgraph event, do its shadow event's other-children have a other-parent that reference that
	 * hashgraph
	 * event?
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff there is a other-child of {@code e} that does not have a other-parent that references {@code e}
	 */
	private static boolean mismatchedOtherParentOfOtherChild(final Event e, final ShadowEvent s) {
		for (final ShadowEvent oc : s.getOtherChildren()) {
			if (mismatched(e, oc.getOtherParent())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * For a given hashgraph event, do its shadow event's other-children have a null other-parent?
	 *
	 * @param e
	 * 		the {@link EventImpl} to check
	 * @param s
	 * 		the {@link ShadowEvent} that should reference it
	 * @return true iff there is a other-child of {@code e} that has a null self-parent
	 */
	private static boolean missingOtherParentOfOtherChild(final Event e, final ShadowEvent s) {
		for (final ShadowEvent oc : s.getOtherChildren()) {
			if (missing(e, oc.getOtherParent())) {
				return true;
			}
		}

		return false;
	}

}
