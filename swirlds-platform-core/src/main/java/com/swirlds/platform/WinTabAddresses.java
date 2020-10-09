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
import java.util.Arrays;

/**
 * The tab in the Browser window that shows available apps, running swirlds, and saved swirlds.
 */
class WinTabAddresses extends WinBrowser.PrePaintableJPanel {
	private static final long serialVersionUID = 1L;
	/** the entire table is in this single Component */
	private JTextArea text;
	/** should the entire window be rebuilt? */
	private boolean redoWindow = true;

	/**
	 * Instantiate and initialize content of this tab.
	 */
	public WinTabAddresses() {
		text = WinBrowser.newJTextArea();
		add(text);
	}

	/** {@inheritDoc} */
	void prePaint() {
		if (!redoWindow) {
			return;
		}
		redoWindow = false;
		String s = "";
		synchronized (Browser.platforms) {
			for (Platform p : Browser.platforms) {
				s += "\n" + p.getAddress().getId() + "   " +//
						p.getAddress().getNickname() + "   " + //
						p.getAddress().getSelfName() + "   " +//
						Arrays.toString(p.getAddress().getAddressInternalIpv4())
						+ "   " +//
						p.getAddress().getPortInternalIpv4() + "   " +//
						Arrays.toString(p.getAddress().getAddressExternalIpv4())
						+ "   " +//
						p.getAddress().getPortExternalIpv4();
			}
		}
		s += Utilities.wrap(70, "\n\n" //
				+ "The above are all the member addresses. "
				+ "Each address includes the nickname, name, "
				+ "internal IP address/port and external IP address/port.");

		text.setText(s);
	}
}
