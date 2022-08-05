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

import com.swirlds.common.system.Platform;

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
