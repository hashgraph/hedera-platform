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

import com.swirlds.common.Platform;

import javax.swing.JTextArea;

/**
 * The tab in the Browser window that shows available apps, running swirlds, and saved swirlds.
 */
class WinTabSecurity extends WinBrowser.PrePaintableJPanel {
	private static final long serialVersionUID = 1L;
	Reference swirldId = null;
	JTextArea text;
	String s = "";

	public WinTabSecurity() {
		text = WinBrowser.newJTextArea();
		add(text);
	}

	/** {@inheritDoc} */
	void prePaint() {
		if (WinBrowser.memberDisplayed == null // haven't chosen yet who to display
				|| swirldId != null) { // already set this up once
			return;
		}
		Platform platform = WinBrowser.memberDisplayed.platform;
		swirldId = new Reference(platform.getSwirldId());

		s += "Swirld ID: \n        " + swirldId.to62() + "\n";
		s += swirldId.toWords("        ");
		s += Utilities.wrap(70, "\n\n"
				+ "Each swirld (shared world, shared ledger, shared database) has a unique identifier. "
				+ "The identifier of the current swirld is shown above in two forms. "
				+ "The first is a base-62 encoding between <angled brackets>. "
				+ "The second is a sequence of words. \n\n"
				+ "Assuming more than two thirds of the population are honest, the "
				+ "unique identifier for a given swirld will never change. And if the "
				+ "swirld ever forks or splits or branches, only one branch will keep "
				+ "the same identifier as the original. So that version of the "
				+ "swirld is the 'official' or 'true' successor, and the rest are new swirlds.");

		text.setText(s);
	}
}
