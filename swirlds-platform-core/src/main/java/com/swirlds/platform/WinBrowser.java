/*
 * (c) 2016-2022 Swirlds, Inc.
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * The main browser window. It contains a tabbed pane, which contains the classes with names WinTab*, some
 * of which might themselves contain tabbed panes, whose tabs would be classes named WinTab2*, and so on.
 *
 * The classes WinBrowser, ScrollableJPanel, and UpdatableJPanel, each have an prePaint() method. Once a
 * second, a timer thread calls WinBrowser.prePaint, and then the call is passed down the Component tree, so
 * that every Component can do the slow calculations for what should appear on the screen. Then the thread
 * calls repaint(), to quickly re-render everything. For example, if there is a long calculation necessary
 * to find the text for a JTextArea, then the long calculation is done inside prePaint(), and the fast
 * rendering of the result is done in paintComponent(). This prevents the GUI thread from hanging for too
 * long when there are slow calculations being performed.
 */
class WinBrowser extends JFrame {
	/** needed to serializing */
	private static final long serialVersionUID = 1L;
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** refresh the screen every this many milliseconds */
	final int refreshPeriod = 500;
	/** use this font for all text in the browser window */
	static Font FONT = new Font("SansSerif", Font.PLAIN, 16);
	/** the InfoMember that is currently being shown by all tabs in the browser window */
	static volatile StateHierarchy.InfoMember memberDisplayed = null;
	/** have all the tabs been initialized yet? */
	private boolean didInit = false;

	/** used to refresh the screen periodically */
	private static Timer updater = null;
	/** gap at top of the screen (to let you click on app windows), in pixels */
	private static final int topGap = 40;
	/** light blue used to highlight which member all the tabs are currently displaying */
	static final Color MEMBER_HIGHLIGHT_COLOR = new Color(0.8f, 0.9f, 1.0f);

	static ScrollableJPanel tabSwirlds;
	static ScrollableJPanel tabAddresses;
	static ScrollableJPanel tabCalls;
	static ScrollableJPanel tabPosts;
	static ScrollableJPanel tabNetwork;
	static ScrollableJPanel tabSecurity;

	static JPanel nameBar;
	static JTextArea nameBarLabel;
	/** the nickname and name of the member on local machine currently being viewed */
	static JTextArea nameBarName;
	static JTabbedPane tabbed;

	public void paintComponents(Graphics g) {
		super.paintComponents(g);
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
	}

	/**
	 * A JPanel with an additional method prePaint(), which (possibly slowly) recalculates everything, so
	 * the next repaint() will (quickly) render everything.
	 */
	static abstract class PrePaintableJPanel extends JPanel {
		private static final long serialVersionUID = 1L;

		{// by default, align everything left
			this.setLayout(new FlowLayout(FlowLayout.LEFT));
		}

		/**
		 * Recalculate the contents of each Component, maybe slowly, so that the next repaint() will trigger
		 * a fast render of everything. If this contains any other UpdatableJPanels, then it must call their
		 * prePaint(), too.
		 */
		abstract void prePaint();
	}

	/**
	 * A JScrollPane containing the given UpdatableJPanel.
	 *
	 * @author Leemon
	 */
	static class ScrollableJPanel extends JScrollPane {
		private static final long serialVersionUID = 1L;
		PrePaintableJPanel contents;

		/**
		 * Wrap the given panel in scroll bars, and remember it so that calls to prePaint() can be passed on
		 * to it.
		 */
		ScrollableJPanel(PrePaintableJPanel contents) {
			super(contents);
			this.contents = contents;
			contents.setVisible(true);
		}

		/**
		 * Recalculate the contents of each Component, maybe slowly, so that the next repaint() will trigger
		 * a fast render of everything.
		 */
		void prePaint() {
			if (contents != null) {
				contents.prePaint();
			}
		}
	}

	/** set the component to have a white background, and wrap it in scroll bars */
	private ScrollableJPanel makeScrollableJPanel(PrePaintableJPanel comp) {
		comp.setBackground(Color.WHITE);
		ScrollableJPanel scroll = new ScrollableJPanel(comp);
		scroll.setBackground(Color.WHITE);
		scroll.setVisible(true);
		return scroll;
	}

	/** when clicked on, switch to the Swirlds tab in the browser window */
	private class ClickForTabSwirlds extends MouseAdapter {
		public void mouseClicked(MouseEvent mouseEvent) {
			if (mouseEvent.getButton() == MouseEvent.BUTTON1) { // 3 for right click
				Browser.showBrowserWindow(WinBrowser.tabSwirlds);
			}
		}
	}

	/**
	 * Perform a prePaint to recalculate the contents of each Component, maybe slowly, so that the next
	 * repaint() will trigger a fast render of everything. Then perform a repaint(). This is synchronized
	 * because it is called by a timer once a second, and is also called by the thread that manages the
	 * mouse whenever a user changes a tab in this window or changes a tab in the Network tab.
	 */
	static synchronized void prePaintThenRepaint() {
		try {
			// Don't prePaint nameBar, nameBarLabel, tabbed, or any tab*.

			// perform the equivalent of prePaint on nameBarName:
			if (WinBrowser.memberDisplayed != null) {
				nameBarName.setText(
						"    " + WinBrowser.memberDisplayed.name + "    ");
			} else { // retry once a second until at least one member exists. Then choose the first one.
				if (tabSwirlds != null) {
					((WinTabSwirlds) tabSwirlds.contents)
							.chooseMemberDisplayed();
				}
			}
			// call prePaint() on the current tab, only if it has such a method
			if (tabbed != null) {
				Component comp = tabbed.getSelectedComponent();
				if (comp != null) {
					if (comp instanceof ScrollableJPanel) {
						ScrollableJPanel tabCurrent = (ScrollableJPanel) comp;
						tabCurrent.prePaint();
					}
				}
			}
			WinBrowser win = Browser.browserWindow;
			if (win != null) {
				if (!win.didInit && WinBrowser.memberDisplayed != null
						&& tabSwirlds != null && tabAddresses != null
						&& tabCalls != null && tabPosts != null
						&& tabNetwork != null && tabSecurity != null) {
					win.didInit = true;
					// just once, do an init that does prePaint for everyone, so when we switch tabs, it
					// appears
					// instantly
					tabSwirlds.prePaint();
					tabAddresses.prePaint();
					tabCalls.prePaint();
					tabPosts.prePaint();
					tabNetwork.prePaint();
					tabSecurity.prePaint();
				}
				win.repaint();
			}
		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(),
					"error while prepainting or painting: ", e);
		}
	}

	/**
	 * This constructor creates the contents of the browser window, and creates a new thread to continually
	 * update this window to reflect what is happening in the Browser.
	 */
	public WinBrowser() {
		ActionListener repaintPeriodically = new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				// Do a (possibly slow) prePaint of components, like changing text in a JTextArea text.
				// Then trigger a (fast) redrawing of the components, like rendering the JTextArea text.
				prePaintThenRepaint();
			}
		};

		// The following adjusts the size of everything slightly on MacOS (should use EmptyBorder instead)
		// UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));

		// Search for "TabbedPane." on this page for all UIManager string values:
		// http://www.rgagnon.com/javadetails/JavaUIDefaults.txt

		// The following have no effect on MacOS:
		// UIManager.put("TabbedPane.contentAreaColor ", ColorUIResource.RED);
		// UIManager.put("TabbedPane.selected", ColorUIResource.RED);
		// UIManager.put("TabbedPane.background", ColorUIResource.RED);
		// UIManager.put("TabbedPane.shadow", ColorUIResource.RED);
		// UIManager.put("TabbedPane.borderColor", Color.RED);
		// UIManager.put("TabbedPane.darkShadow", ColorUIResource.RED);
		// UIManager.put("TabbedPane.light", ColorUIResource.RED);
		// UIManager.put("TabbedPane.highlight", ColorUIResource.RED);
		// UIManager.put("TabbedPane.focus", ColorUIResource.RED);
		// UIManager.put("TabbedPane.unselectedBackground", ColorUIResource.RED);
		// UIManager.put("TabbedPane.selectHighlight", ColorUIResource.RED);
		// UIManager.put("TabbedPane.tabAreaBackground", ColorUIResource.RED);
		// UIManager.put("TabbedPane.borderHightlightColor", ColorUIResource.RED);
		// UIManager.put("TabbedPane.shadow", ColorUIResource.RED);

		nameBar = new JPanel();
		nameBarLabel = new JTextArea();
		nameBarName = new JTextArea();
		tabbed = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
		tabSwirlds = makeScrollableJPanel(new WinTabSwirlds());
		tabAddresses = makeScrollableJPanel(new WinTabAddresses());
		tabCalls = makeScrollableJPanel(new WinTabCalls());
		tabPosts = makeScrollableJPanel(new WinTabPosts());
		tabNetwork = makeScrollableJPanel(new WinTabNetwork());
		tabSecurity = makeScrollableJPanel(new WinTabSecurity());

		Rectangle winRect = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getMaximumWindowBounds();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		Dimension dim = new Dimension(winRect.width, winRect.height - topGap);
		setSize(dim);
		setPreferredSize(dim);

		setLocation(winRect.x, winRect.y + topGap);
		setFocusable(true);
		requestFocusInWindow();
		addWindowListener(stopper());

		nameBar.setLayout(new GridBagLayout());
		nameBar.setPreferredSize(new Dimension(1000, 42));

		MouseListener listener = new ClickForTabSwirlds();

		nameBarLabel.setText("Displaying information for:  ");
		nameBarLabel.setEditable(false);
		nameBarLabel.setEnabled(false);
		nameBarLabel.setDisabledTextColor(Color.BLACK);
		nameBarLabel.addMouseListener(listener);
		nameBarLabel.setCaretPosition(0);

		nameBarName.setText(""); // this will be set again each time the member is chosen
		nameBarName.setEditable(false);
		nameBarName.setEnabled(false);
		nameBarName.setDisabledTextColor(Color.BLACK);
		nameBarName.addMouseListener(listener);

		nameBar.add(nameBarLabel);
		nameBar.add(nameBarName);
		nameBar.addMouseListener(listener);
		tabbed.addTab("Swirlds", tabSwirlds);
		// tabbed.addTab("Calls", tabCalls);
		// tabbed.addTab("Posts", tabPosts);
		tabbed.addTab("Addresses", tabAddresses);
		tabbed.addTab("Network", tabNetwork);
		tabbed.addTab("Security", tabSecurity);
		goTab(tabNetwork); // select and show this one first

		setBackground(Color.WHITE); // this color flashes briefly at startup, then is hidden
		nameBar.setBackground(Color.WHITE); // color of name bar outside label and name
		nameBarLabel.setBackground(Color.WHITE); // color of name bar label
		nameBarName.setBackground(MEMBER_HIGHLIGHT_COLOR); // color of name
		tabbed.setBackground(Color.WHITE); // color of non-highlighted tab buttons
		tabbed.setForeground(Color.BLACK); // color of words on the tab buttons

		setLayout(new BorderLayout());
		add(nameBar, BorderLayout.PAGE_START);
		add(tabbed, BorderLayout.CENTER);
		// add(tabPosts, BorderLayout.CENTER);
		SwirldMenu.addTo(null, this, 40);
		pack();
		setVisible(true);

		updater = new Timer(refreshPeriod, repaintPeriodically);
		updater.start();
	}

	/**
	 * Switch to the tab containing the given contents, and bring the window forward.
	 *
	 * @param contents
	 * 		the contents of that tab
	 */
	void goTab(ScrollableJPanel contents) {
		requestFocus(true);
		tabbed.setSelectedComponent(contents);
		prePaintThenRepaint();
	}

	/**
	 * Add this to a window as a listener so that when the window closes, so does the entire program,
	 * including the browser and all platforms.
	 *
	 * @return a listener that responds to the window closing
	 */
	static WindowAdapter stopper() {
		return new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
				Browser.stopBrowser();
			}
		};
	}

	/**
	 * Instantiates and returns a JTextArea whose settings are suitable for use inside the browser window's
	 * scroll area in a tab.
	 */
	static JTextArea newJTextArea() {
		return newJTextArea("");
	}

	/**
	 * Instantiates and returns a JTextArea whose settings are suitable for use inside the browser window's
	 * scroll area in a tab.
	 */
	static JTextArea newJTextArea(String text) {
		JTextArea txt = new JTextArea(0, 0);
		((DefaultCaret) txt.getCaret())
				.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		txt.setBackground(Color.WHITE);
		txt.setForeground(Color.BLACK);
		txt.setDisabledTextColor(Color.BLACK);
		txt.setFont(WinBrowser.FONT);
		txt.setEditable(false);
		txt.setEnabled(false);
		txt.setText(text);
		txt.setVisible(true);
		return txt;
	}
}
