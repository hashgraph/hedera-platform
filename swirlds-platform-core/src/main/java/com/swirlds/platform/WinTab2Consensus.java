/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
			long r1 = platform.getConsensus().getDeleteRound();
			long r2 = platform.getConsensus().getFameDecidedBelow();
			long r3 = platform.getConsensus().getMaxRound();
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
