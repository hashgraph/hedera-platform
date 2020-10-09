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
package com.swirlds.platform;

import com.swirlds.platform.StateHierarchy.InfoMember;
import com.swirlds.platform.state.SignedStateInfo;

import javax.swing.JTextArea;
import java.awt.Font;
import java.awt.Graphics;

/**
 * The tab in the Browser window that shows network speed, transactions per second, etc.
 */
class WinTab2Consensus extends WinBrowser.PrePaintableJPanel {
	/** this is needed for serializing */
	private static final long serialVersionUID = 1L;

	/** the entire table is in this single Component */
	private JTextArea text;

	/**
	 * Instantiate and initialize content of this tab.
	 */
	public WinTab2Consensus() {
		text = WinBrowser.newJTextArea();
		add(text);
	}

	/** {@inheritDoc} */
	@Override
	void prePaint() {
		try {
			if (WinBrowser.memberDisplayed == null) {
				return;
			}
			AbstractPlatform platform = ((InfoMember) (WinBrowser.memberDisplayed)).platform;
			String s = "";
			s += platform.getPlatformName();
			long r1 = platform.getHashgraph().getDeleteRound();
			long r2 = platform.getHashgraph().getLastRoundDecided();
			long r3 = platform.getHashgraph().getMaxRound();
			long r0 = platform.getSignedStateManager().getLastCompleteRound();

			if (r1 == -1) {
				s += "\n           = latest deleted round-created";
			} else {
				s += String.format("\n%,10d = latest deleted round-created",
						r1);
			}
			if (r0 == -1) {
				s += String.format(
						"\n           = latest supermajority signed state round-decided");
			} else {
				s += String.format(
						"\n%,10d = latest supermajority signed state round-decided (deleted round +%,d)",
						r0, r0 - r1);
			}
			s += String.format(
					"\n%,10d = latest round-decided (delete round +%,d)", r2,
					r2 - r1);
			s += String.format(
					"\n%,10d = latest round-created (deleted round +%,d)", r3,
					r3 - r1);

			// the hash of a signed state is: Reference.toHex(state.getHash(), 0, 2)

			SignedStateInfo[] stateInfo = platform.getSignedStateManager().getSignedStateInfo();
			SignedStateInfo first = null;
			if (stateInfo.length > 0) {
				first = stateInfo[0];
			}
			long d = first == null ? 0 : first.getLastRoundReceived();
			// count of digits in round number
			d = String.format("%,d", d).length();
			// add 2 because earlier rounds might be 2 shorter, like 998 vs 1,002
			d += 2;

			s += "\n     Signed state for round:            ";
			for (SignedStateInfo state : stateInfo) {
				if (state != null && state.getSigSet() != null) {
					s += String.format("%," + d + "d ",
							state.getLastRoundReceived());
				}
			}

			s += "\n     Signatures collected:              ";
			for (SignedStateInfo state : stateInfo) {
				if (state != null && state.getSigSet() != null) {
					int c = state.getSigSet().getCount();
					s += String.format("%," + d + "d ", c);
				}
			}

			s += "\n                                        ";
			for (SignedStateInfo state : stateInfo) {
				if (state != null && state.getSigSet() != null) {
					int c = state.getSigSet().getCount();
					int size;

					if (Settings.enableBetaMirror) {
						// if beta mirror logic is enabled then use the count of members with stake
						size = platform.getAddressBook().getNumberWithStake();

						if (platform.isZeroStakeNode()) {
							size++;
						}
					} else {
						size = platform.getNumMembers();
					}

					s += String.format("%" + d + "s ", c == size ? "___"
							: state.getSigSet().isComplete() ? "ooo" : "###");
				}
			}


			s += Utilities.wrap(70, "\n\n"
					+ "After each round, there is a consensus state, which is the result "
					+ "of all the transactions so far, in their consensus order. Each "
					+ "member signs that state, and sends out their signature. \n\n"
					+ "The above "
					+ "shows how one member (shown at the top of this window) is collecting "
					+ "those signatures. It shows how many transactions have achieved "
					+ "consensus so far, and the latest round number that has its "
					+ " events discarded, the latest that has collected signatures from members "
					+ "with at least 1/3 of the total stake, the latest that has its "
					+ "famous witnesses decided (which is the core of the hashgraph consensus "
					+ "algorithm), and the latest that has at least one known event. \n\n"
					+ "For each round, the table shows the round number, then the "
					+ "count of how many signatures are collected so far, then an indication "
					+ "of whether this represents everyone (___), or not everyone but at least "
					+ "one third of stake (ooo), or even less than that (###).");
			text.setFont(new Font("monospaced", Font.PLAIN, 14));
			text.setText(s);
		} catch (java.util.ConcurrentModificationException err) {
			// We started displaying before all the platforms were added. That's ok.
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
	}
}
