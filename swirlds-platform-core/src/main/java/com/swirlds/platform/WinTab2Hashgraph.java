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

import com.swirlds.common.AddressBook;
import com.swirlds.common.events.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JPanel;
import java.awt.AWTException;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.BiFunction;

import static com.swirlds.logging.LogMarker.EXCEPTION;

class WinTab2Hashgraph extends WinBrowser.PrePaintableJPanel {
	/** needed for serializing */
	private static final long serialVersionUID = 1L;
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** outline of labels */
	static final Color LABEL_OUTLINE = new Color(255, 255, 255);
	/** unknown-fame witness, non-consensus */
	static final Color LIGHT_RED = new Color(192, 0, 0);
	/** unknown-fame witness, consensus (which can't happen) */
	static final Color DARK_RED = new Color(128, 0, 0);
	/** unknown-fame witness, consensus */
	static final Color LIGHT_GREEN = new Color(0, 192, 0);
	/** famous witness, non-consensus */
	static final Color DARK_GREEN = new Color(0, 128, 0);
	/** famous witness, consensus */
	static final Color LIGHT_BLUE = new Color(0, 0, 192);
	/** non-famous witness, non-consensus */
	static final Color DARK_BLUE = new Color(0, 0, 128);
	/** non-famous witness, consensus */
	static final Color LIGHT_GRAY = new Color(160, 160, 160);
	/** non-witness, non-consensus */
	static final Color DARK_GRAY = new Color(0, 0, 0);
	/** non-witness, consensus */
	public AbstractPlatform platform = null;
	/** app is run by this */
	public int selfId;

	/** the panel with checkboxes */
	JPanel checkboxesPanel;
	/** the panel that has the picture of the hashgraph */
	Picture picturePanel;

	/** paintComponent will draw this copy of the set of events */
	private Event[] eventsCache;
	/**
	 * the number of members in the addressBook
	 */
	private int numMembers = -1;
	/** the nicknames of all the members */
	private String[] names;

	// the following allow each member to have multiple columns so lines don't cross

	// number of columns (more than number of members if preventCrossings)
	private int numColumns;
	// mems2col[a][b] = which member-b column is adjacent to some member-a column
	private int mems2col[][];
	// col2mems[c][0] = the member for column c, col2mems[c][1] = second member or -1 if none
	private int col2mems[][];

	// if checked, freeze the display (don't update it)
	private Checkbox freezeCheckbox;
	// if checked, color vertices only green (non-consensus) or blue (consensus)
	private Checkbox simpleColorsCheckbox;
	// if checked, use multiple columns per member to void lines crossing
	private Checkbox expandCheckbox;

	// the following control which labels to print on each vertex

	// the round number for the event
	private Checkbox labelRoundCheckbox;
	// the consensus round received for the event
	private Checkbox labelRoundRecCheckbox;
	// the consensus order number for the event
	private Checkbox labelConsOrderCheckbox;
	// the consensus time stamp for the event
	private Checkbox labelConsTimestampCheckbox;
	// the generation number for the event
	private Checkbox labelGenerationCheckbox;
	// the ID number of the member who created the event
	private Checkbox labelCreatorCheckbox;
	// the sequence number for that creator (starts at 0)
	private Checkbox labelSeqCheckbox;

	// only draw this many events, at most
	private TextField eventLimit;

	// used to store an image when the freeze checkbox is checked
	private BufferedImage image = null;

	/**
	 * Instantiate and initialize content of this tab.
	 */
	public WinTab2Hashgraph() {
		// initIfNeeded();
	}

	private void initIfNeeded() {
		if (platform != null || WinBrowser.memberDisplayed == null) {
			return;
		}

		platform = WinBrowser.memberDisplayed.platform;

		// the tab contains the pairPanel, which contains:
		// at (0,0) checkboxesPanel (with weight 0)
		// at (1,0) picture (which is the hashgraph) (with weight 1)

		/////////////////// create each component for checkboxesPanel ///////////////////

		String[] pars = new String[] { "0", "0", "0", "0", "0", "0", "0", "0",
				"0", "0", "0", "all" };
		BiFunction<Integer, String, Checkbox> cb = (n, s) -> new Checkbox(s,
				null, pars.length <= n ? false : pars[n].trim().equals("1"));

		int p = 0; // which parameter to use
		freezeCheckbox = cb.apply(p++, "Freeze: don't change this window");
		simpleColorsCheckbox = cb.apply(p++,
				"Colors: blue=consensus, green=not");
		expandCheckbox = cb.apply(p++, "Expand: wider so lines don't cross");
		labelRoundCheckbox = cb.apply(p++, "Labels: Round created");
		labelRoundRecCheckbox = cb.apply(p++,
				"Labels: Round received (consensus)");
		labelConsOrderCheckbox = cb.apply(p++, "Labels: Order (consensus)");
		labelConsTimestampCheckbox = cb.apply(p++,
				"Labels: Timestamp (consensus)");
		labelGenerationCheckbox = cb.apply(p++, "Labels: Generation");
		labelCreatorCheckbox = cb.apply(p++, "Labels: Creator ID");
		labelSeqCheckbox = cb.apply(p++, "Labels: Creator Seq");

		expandCheckbox.setState(platform.getAddressBook().getSize() <= 6); // expand if not many members

		eventLimit = new TextField(pars.length <= p ? "" : pars[p].trim(), 5);
		p++;

		freezeCheckbox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					try {// capture a bitmap of "picture" from the screen
						image = (new Robot()).createScreenCapture(new Rectangle(
								picturePanel.getLocationOnScreen(),
								picturePanel.getVisibleRect().getSize()));
						// to write the image to disk:
						// ImageIO.write(image, "jpg", new File("image.jpg"));
					} catch (AWTException err) {
					}
				} else if (e.getStateChange() == ItemEvent.DESELECTED) {
					image = null; // erase the saved image, stop freezing
				}
			}
		});

		Component[] comps = new Component[] { freezeCheckbox,
				simpleColorsCheckbox, expandCheckbox, labelRoundCheckbox,
				labelRoundRecCheckbox, labelConsOrderCheckbox,
				labelConsTimestampCheckbox, labelGenerationCheckbox,
				labelCreatorCheckbox, labelSeqCheckbox };

		/////////////////// create checkboxesPanel ///////////////////

		checkboxesPanel = new JPanel();
		checkboxesPanel.setLayout(new GridBagLayout());
		checkboxesPanel.setBackground(Color.WHITE);
		checkboxesPanel.setVisible(true);
		GridBagConstraints constr = new GridBagConstraints();
		constr.fill = GridBagConstraints.NONE; // don't stretch components
		constr.anchor = GridBagConstraints.FIRST_LINE_START; // left align each component in its cell
		constr.weightx = 0; // don't put extra space in the middle
		constr.weighty = 0;
		constr.gridx = 0; // start in upper-left cell
		constr.gridy = 0;
		constr.insets = new Insets(0, 10, -4, 0); // add external padding on left, remove from bottom
		constr.gridheight = 1;
		constr.gridwidth = GridBagConstraints.RELATIVE; // first component is only second-to-last-on-row
		for (Component c : comps) {
			checkboxesPanel.add(c, constr);
			constr.gridwidth = GridBagConstraints.REMAINDER; // all but the first are last-on-row
			constr.gridy++;
		}
		checkboxesPanel.add(new Label(" "), constr); // skip a line
		constr.gridy++;
		constr.gridwidth = 1; // each component is one cell
		checkboxesPanel.add(new Label("Display the last "), constr);
		constr.gridx++;
		checkboxesPanel.add(eventLimit, constr);
		constr.gridx++;
		constr.gridwidth = GridBagConstraints.RELATIVE;
		checkboxesPanel.add(new Label(" events"), constr);
		constr.gridx++;
		constr.gridwidth = GridBagConstraints.REMAINDER;
		checkboxesPanel.add(new Label(""), constr);
		constr.gridx = 0;
		constr.gridy++;
		checkboxesPanel.add(new Label(" "), constr);
		constr.gridy++;
		checkboxesPanel.add(WinBrowser.newJTextArea(Utilities.wrap(50,
				"Witnesses are colored circles, non-witnesses are black/gray. "
						+ "Dark circles are part of the consensus, light are not. "
						+ "Fame is true for green, false for blue, unknown for red. ")),
				constr);
		constr.gridy++;
		constr.weighty = 1.0; // give this spacer all the leftover vertical space in column
		checkboxesPanel.add(new Label(" "), constr); // the spacer that is stretched vertically

		/////////////////// create picture ///////////////////

		picturePanel = new Picture();
		picturePanel.setLayout(new GridBagLayout());
		picturePanel.setBackground(Color.WHITE);
		picturePanel.setVisible(true);

		/////////////////// create pairPanel (contains checkboxesPanel, picturePnel) ///////////////////

		JPanel pairPanel = new JPanel();
		pairPanel.setLayout(new GridBagLayout());
		pairPanel.setBackground(Color.WHITE);
		pairPanel.setVisible(true);
		GridBagConstraints c3 = new GridBagConstraints();
		c3.fill = GridBagConstraints.NONE; // don't stretch components
		c3.anchor = GridBagConstraints.FIRST_LINE_START; // left align each component in its cell
		c3.gridx = 0;
		c3.gridy = 0;
		c3.gridheight = 1;
		c3.gridwidth = GridBagConstraints.RELATIVE;
		c3.gridheight = GridBagConstraints.REMAINDER;
		c3.fill = GridBagConstraints.BOTH;
		c3.weightx = 0; // don't put extra space in the checkbox side
		c3.weighty = 0;
		pairPanel.add(checkboxesPanel, c3);
		c3.gridx = 1;
		c3.gridwidth = GridBagConstraints.REMAINDER;
		c3.gridheight = GridBagConstraints.REMAINDER;
		c3.weightx = 1.0f;
		c3.weighty = 1.0f;
		c3.fill = GridBagConstraints.BOTH;
		pairPanel.add(picturePanel, c3);
		revalidate();

		/////////////////// create spacer ///////////////////

		JPanel spacer = new JPanel();
		spacer.setBackground(Color.YELLOW);
		spacer.setVisible(true);

		/////////////////// add everything to this ///////////////////

		setLayout(new GridBagLayout());// put panel at top, then spacer below it
		GridBagConstraints c4 = new GridBagConstraints();
		c4.anchor = GridBagConstraints.FIRST_LINE_START;
		c4.gridx = 0;
		c4.gridy = 0;
		c4.weightx = 1.0f;
		c4.weighty = 1.0f;
		c4.gridwidth = GridBagConstraints.REMAINDER;
		c3.fill = GridBagConstraints.BOTH;
		add(pairPanel, c4);
		picturePanel.setVisible(true);

		// Dimension ps = WinBrowser.tabbed.getPreferredSize();
		// ps.width -= 150;
		// ps.height -= 500;
		Dimension ps = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		ps.width -= 150;
		ps.height -= 200;
		pairPanel.setPreferredSize(ps);
		revalidate();
	}

	// format the consensusTimestamp label
	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:m:s.n")
			.withLocale(Locale.US).withZone(ZoneId.systemDefault());

	/**
	 * Return the color for an event based on calculations in the consensus algorithm A non-witness is gray,
	 * and a witness has a color of green (famous), blue (not famous) or red (undecided fame). When the
	 * event becomes part of the consensus, its color becomes darker.
	 *
	 * @param event
	 * 		the event to color
	 * @return its color
	 */
	private Color eventColor(Event event) {
		if (simpleColorsCheckbox.getState()) { // if checkbox checked
			return event.isConsensus() ? LIGHT_BLUE : LIGHT_GREEN;
		}
		if (!event.isWitness()) {
			return event.isConsensus() ? DARK_GRAY : LIGHT_GRAY;
		}
		if (!event.isFameDecided()) {
			return event.isConsensus() ? DARK_RED : LIGHT_RED;
		}
		if (event.isFamous()) {
			return event.isConsensus() ? DARK_GREEN : LIGHT_GREEN;
		}
		return event.isConsensus() ? DARK_BLUE : LIGHT_BLUE;
	}

	void prePaint() {
		initIfNeeded(); // only once will this do something real
		platform = WinBrowser.memberDisplayed.platform; // update in case user changed it
		if (checkboxesPanel == null || freezeCheckbox == null
				|| freezeCheckbox.getState()) {
			return; // freeze when requested, or when it hasn't been created yet
		}
		// getAllEvents may be a slow operation, so do it here not in paintComponent
		eventsCache = platform.getAllEvents();

		// The drawing will have to be redone from scratch every time,
		// so not much is done in prePaint(). Most things are done in paintComponent().
	}

	/**
	 * This panel has the hashgraph picture, and appears in the window to the right of all the settings.
	 */
	private class Picture extends JPanel {
		private static final long serialVersionUID = 1L;
		int ymin, ymax, width, n;
		double r;
		long minGen, maxGen;
		// where to draw next in the window, and the font height
		int row, textLineHeight;

		/** find x position on the screen for event e2 which has an other-parent of e1 (or null if none) */
		private int xpos(Event e1, Event e2) {
			// the gap between left side of screen and leftmost column
			// is marginFraction times the gap between columns (and similarly for right side)
			final double marginFraction = 0.5;
			// gap between columns
			final int betweenGap = (int) (width
					/ (numColumns - 1 + 2 * marginFraction));
			// gap between leftmost column and left edge (and similar on right)
			final int sideGap = (int) (betweenGap * marginFraction);

			if (e1 != null) { // find the column for e2 next to the column for e1
				return sideGap + mems2col[(int) e1.getCreatorId()][(int) e2
						.getCreatorId()] * betweenGap;
			} else { // there is no e1, so pick one of the e2 columns arbitrarily (next to 0 or 1). If there is only 1
				// member, avoid the array out of bounds exception
				return sideGap + mems2col[e2.getCreatorId() == 0 ? numColumns == 1 ? 0 : 1
						: 0][(int) e2.getCreatorId()] * betweenGap;
			}
		}

		// find y position on the screen for an event
		private int ypos(Event event) {
			return (event == null) ? -100
					: (int) (ymax
					- r * (1 + 2 * (event.getGeneration() - minGen)));
		}

		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			try {
				if (image != null) {
					g.drawImage(image, 0, 0, null);
					return;
				}
				if (platform == null) {
					initIfNeeded();
					return;
				}
				g.setFont(new Font(Font.MONOSPACED, 12, 12));
				FontMetrics fm = g.getFontMetrics();
				int fa = fm.getMaxAscent();
				int fd = fm.getMaxDescent();
				textLineHeight = fa + fd;
				int numMem = platform.getNumMembers();
				calcMemsColNames();
				width = getWidth();

				row = 1;

				int height1 = (row - 1) * textLineHeight;    // text area at the top
				int height2 = getHeight() - height1; // the main display, below the text
				g.setColor(Color.BLACK);
				ymin = (int) Math.round(height1 + 0.025 * height2);
				ymax = (int) Math.round(height1 + 0.975 * height2)
						- textLineHeight;
				for (int i = 0; i < numColumns; i++) {
					String name;
					if (col2mems[i][1] == -1) {
						name = "" + names[col2mems[i][0]];
					} else {
						name = "" + names[col2mems[i][0]] + "|"
								+ names[col2mems[i][1]];
					}
					// the gap between left side of screen and leftmost column
					// is marginFraction times the gap between columns (and similarly for right side)
					final double marginFraction = 0.5;
					// gap between columns
					final int betweenGap = (int) (width
							/ (numColumns - 1 + 2 * marginFraction));
					// gap between leftmost column and left edge (and similar on right)
					final int sideGap = (int) (betweenGap * marginFraction);
					int x = sideGap + (i) * betweenGap;
					g.drawLine(x, ymin, x, ymax);
					Rectangle2D rect = fm.getStringBounds(name, g);
					g.drawString(name, (int) (x - rect.getWidth() / 2),
							(int) (ymax + rect.getHeight()));
				}

				Event[] events = eventsCache;
				if (events == null) { // in case a screen refresh happens before any events
					return;
				}
				int maxEvents;
				try {
					maxEvents = Math.max(0,
							Integer.parseInt(eventLimit.getText()));
				} catch (NumberFormatException err) {
					maxEvents = 0;
				}

				if (maxEvents > 0) {
					events = Arrays.copyOfRange(events,
							Math.max(0, events.length - maxEvents),
							events.length);
				}

				minGen = Integer.MAX_VALUE;
				maxGen = Integer.MIN_VALUE;
				for (Event event : events) {
					minGen = Math.min(minGen, event.getGeneration());
					maxGen = Math.max(maxGen, event.getGeneration());
				}
				maxGen = Math.max(maxGen, minGen + 2);
				n = numMem + 1;
				double gens = maxGen - minGen;
				double dy = (ymax - ymin) * (gens - 1) / gens;
				r = Math.min(width / n / 4, dy / gens / 2);
				int d = (int) (2 * r);

				// for each event, draw 2 downward lines to its parents
				for (Event event : events) {
					g.setColor(eventColor(event));
					Event e1 = event.getSelfParent();
					Event e2 = event.getOtherParent();
					if (e1 != null && e1.getGeneration() >= minGen) {
						g.drawLine(xpos(e2, event), ypos(event),
								xpos(e2, event), ypos(e1));
					}
					if (e2 != null && e2.getGeneration() >= minGen) {
						g.drawLine(xpos(e2, event), ypos(event),
								xpos(event, e2), ypos(e2));
					}
				}

				// for each event, draw its circle
				for (Event event : events) {
					Event e2 = event.getOtherParent();
					Color color = eventColor(event);
					g.setColor(color);
					g.fillOval(xpos(e2, event) - d / 2, ypos(event) - d / 2, d,
							d);
					g.setFont(g.getFont().deriveFont(Font.BOLD));

					String s = "";

					if (labelRoundCheckbox.getState()) {
						s += " " + event.getRoundCreated();
					}
					if (labelRoundRecCheckbox.getState()
							&& event.getRoundReceived() > 0) {
						s += " " + event.getRoundReceived();
					}
					// if not consensus, then there's no order yet
					if (labelConsOrderCheckbox.getState()
							&& event.isConsensus()) {
						s += " " + event.getConsensusOrder();
					}
					if (labelConsTimestampCheckbox.getState()) {
						Instant t = event.getConsensusTimestamp();
						if (t != null) {
							s += " " + formatter.format(t);
						}
					}
					if (labelGenerationCheckbox.getState()) {
						s += " " + event.getGeneration();
					}
					if (labelCreatorCheckbox.getState()) {
						s += " " + event.getCreatorId(); // ID number of member who created it
					}
					if (labelSeqCheckbox.getState()) {
						s += " " + event.getSeq(); // sequence number for the creator (starts at 0)
					}
					if (s != "") {
						Rectangle2D rect = fm.getStringBounds(s, g);
						int x = (int) (xpos(e2, event) - rect.getWidth() / 2.
								- fa / 4.);
						int y = (int) (ypos(event) + rect.getHeight() / 2.
								- fd / 2);
						g.setColor(LABEL_OUTLINE);
						g.drawString(s, x - 1, y - 1);
						g.drawString(s, x + 1, y - 1);
						g.drawString(s, x - 1, y + 1);
						g.drawString(s, x + 1, y + 1);
						g.setColor(color);
						g.drawString(s, x, y);
					}
				}
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(), "error while painting: {}",
						e);
			}
		}
	}

	/**
	 * In order to draw this "expanded" hashgraph (where each member has multiple columns and lines don't
	 * cross), we need several data tables. So fill in four arrays: numMembers, mems2col, col2mems, and
	 * names, if they haven't already been filled in, or if the number of members has changed.
	 */
	private void calcMemsColNames() {
		final AddressBook addressBook = platform.getHashgraph().getAddressBook();
		final int m = addressBook.getSize();
		if (m != numMembers) {
			numMembers = m;
			names = new String[m];
			for (int i = 0; i < m; i++) {
				names[i] = addressBook.getAddress(i).getNickname();
			}
		}

		final boolean expand = (expandCheckbox.getState()); // is checkbox checked?

		if (col2mems != null && (!expand && col2mems.length == m
				|| expand && col2mems.length == m * (m - 1) / 2 + 1)) {
			return; // don't recalculate twice in a row for the same size
		}

		// fix corner cases missed by the formulas here
		if (numMembers == 1) {
			numColumns = 1;
			col2mems = new int[][] { { 0, -1 } };
			mems2col = new int[][] { { 0 } };
			return;
		} else if (numMembers == 2) {
			numColumns = 2;
			col2mems = new int[][] { { 0, -1 }, { 1, -1 } };
			mems2col = new int[][] { { 0, 1 }, { 0, 0 } };
			return;
		}

		if (!expand) { // if unchecked so only one column per member, then the arrays are trivial
			numColumns = m;
			mems2col = new int[m][m];
			col2mems = new int[numColumns][2];
			for (int i = 0; i < m; i++) {
				col2mems[i][0] = i;
				col2mems[i][1] = -1;
				for (int j = 0; j < m; j++) {
					mems2col[i][j] = j;
				}
			}
			return;
		}

		numColumns = m * (m - 1) / 2 + 1;
		mems2col = new int[m][m];
		col2mems = new int[numColumns][2];

		for (int x = 0; x < m * (m - 1) / 2 + 1; x++) {
			final int d1 = ((m % 2) == 1) ? 0 : 2 * ((x - 1) / (m - 1)); // amount to add to x to skip
			// columns
			col2mems[x][0] = col2mem(m, d1 + x);// find even m answer by asking for m+1 with skipped cols
			col2mems[x][1] = (((m % 2) == 1) || ((x % (m - 1)) > 0) || (x == 0)
					|| (x == m * (m - 1) / 2)) ? -1 : col2mem(m, d1 + x + 2);
			final int d = ((m % 2) == 1) ? 0 : 2 * (x / (m - 1)); // amount to add to x to skip columns
			final int a = col2mem(m, d + x);
			final int b = col2mem(m, d + x + 1);
			if (x < m * (m - 1) / 2) { // on the last iteration, x+1 is invalid, so don't record it
				mems2col[b][a] = x;
				mems2col[a][b] = x + 1;
			}
		}
	}

	/**
	 * return the member number for column x, if there are m members. This is set up so each member appears
	 * in multiple columns, and for any two members there will be exactly one location where they have
	 * adjacent columns.
	 * <p>
	 * The pattern used comes from a Eulerian cycle on the complete graph of m members, formed by combining
	 * floor(m/2) disjoint Eulerian paths on the complete graph of m-1 members.
	 * <p>
	 * This method assumes an odd number of members. If there are an even number of members, then assume
	 * there is one extra member, use this method, then delete the columns of the fictitious member, and
	 * combine those columns on either side of each deleted one.
	 *
	 * @param m
	 * 		the number of members (must be odd)
	 * @param x
	 * 		the column (from 0 to 1+m*(m-1)/2)
	 * @return the member number (from 0 to m-1)
	 */
	private int col2mem(int m, int x) {
		m = (m / 2) * 2 + 1; // if m is even, round up to the nearest odd
		final int i = (x / m) % (m / 2); // the ith Eulerian path on the complete graph of m-1 vertices
		final int j = x % m;       // position along that ith path

		if (j == m - 1)
			return m - 1; // add the mth vertex after each Eulerian path to get a Eulerian cycle
		if ((j % 2) == 0)
			return i + j / 2; // in a given path, every other vertex counts up
		return (m - 2 + i - (j - 1) / 2) % (m - 1); // and every other vertex counts down
	}
}
