/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.platform;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** The tab in the Browser window that shows network speed, transactions per second, etc. */
class WinTabNetwork extends WinBrowser.PrePaintableJPanel {
    private static final long serialVersionUID = 1L;
    /** have all the tabs been initialized yet? */
    private boolean didInit = false;

    /**
     * a tabbed pane of different views of the network. Should change it from JTabbedPane to
     * something custom that looks nicer
     */
    JTabbedPane tabbed = new JTabbedPane(JTabbedPane.LEFT, JTabbedPane.WRAP_TAB_LAYOUT);

    WinTab2Stats tabStats = new WinTab2Stats();
    WinTab2Hashgraph tabHashgraph = new WinTab2Hashgraph();
    WinTab2Consensus tabConsensus = new WinTab2Consensus();

    /** Instantiate and initialize content of this tab. */
    public WinTabNetwork() {
        Rectangle winRect =
                GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

        Dimension d = new Dimension(winRect.width - 20, winRect.height - 110);

        setLocation(winRect.x, winRect.y);
        setFocusable(true);
        requestFocusInWindow();

        tabbed.addTab("Stats", tabStats);
        tabbed.addTab("Hashgraph", tabHashgraph);
        tabbed.addTab("Consensus", tabConsensus);
        tabbed.setSelectedComponent(tabStats); // default to Hashgraph
        tabbed.addChangeListener(
                new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        if (e.getSource() instanceof JTabbedPane) {
                            WinBrowser.prePaintThenRepaint();
                        }
                    }
                });

        setBackground(Color.WHITE);
        tabbed.setBackground(Color.WHITE);
        tabStats.setBackground(Color.WHITE);
        tabHashgraph.setBackground(Color.WHITE);
        tabConsensus.setBackground(Color.WHITE);

        tabStats.setMinimumSize(d);
        tabHashgraph.setMaximumSize(d);
        tabConsensus.setMaximumSize(d);
        add(tabbed);
        setVisible(true);
        revalidate();
    }

    /** {@inheritDoc} */
    void prePaint() {
        if (tabbed == null) {
            return;
        }
        if (!didInit && WinBrowser.memberDisplayed != null) {
            didInit = true;
            // just do this once, so we can switch to tabs in the future and see them instantly
            tabStats.prePaint();
            tabHashgraph.prePaint();
            tabConsensus.prePaint();
        }

        Component comp = tabbed.getSelectedComponent();
        if (!(comp instanceof WinBrowser.PrePaintableJPanel)) {
            return;
        }
        WinBrowser.PrePaintableJPanel panel = (WinBrowser.PrePaintableJPanel) comp;
        panel.prePaint();
    }

    /**
     * Switch to tab number n, and bring the window forward.
     *
     * @param n the index of the tab to select
     */
    void goTab(int n) {
        requestFocus(true);
        tabbed.setSelectedIndex(n);
        WinBrowser.prePaintThenRepaint();
    }
}
