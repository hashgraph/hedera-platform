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

import com.swirlds.common.internal.StatsBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.swirlds.logging.LogMarker.EXCEPTION;

class WinTab2Stats extends WinBrowser.PrePaintableJPanel {
	private static final long serialVersionUID = 1L;
	/** number of columns of statistics boxes to display */
	final int numCols = 8;
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** a sentence describing each stat */
	private JTextArea descriptions;

	private final Color LIGHT_GRAY = new Color(0.9f, 0.9f, 0.9f);
	private final Color DARK_GRAY = new Color(0.8f, 0.8f, 0.8f);

	/** all the TextArea objects, one per statistic */
	private JLabel[] statBoxes;
	/** strings describing each statistic (name, description, formatting string) */
	private String[][] statInfo;
	/** Each chart is a JPanel containing two Chart objects: recent history and all history */
	private JPanel[] charts;
	/** the panel containing all the stats boxes (one name, one stat each) */
	private JPanel boxesPanel;
	/** the panel containing all the visible charts (if any) */
	private JPanel chartsPanel;
	/** the labels to put on the X axis */
	private String[] xLabel;
	/** the value of each label to put on the X axis */
	private double[] xLabelVal;
	/** the labels to put on the Y axis */
	private String[] yLabel;
	/** the value of each label to put on the Y axis */
	private double[] yLabelVal;
	/** the nth box in row major order is the rm2cm[n] box in column major */
	private int[] rm2cm;
	/** the nth box in column major order is the cm2rm[n] box in row major */
	private int[] cm2rm;

	/**
	 * Instantiate and initialize content of this tab.
	 */
	public WinTab2Stats() {
		Statistics stats = WinBrowser.memberDisplayed.platform.getStats();
		statInfo = stats.getAvailableStats();
		int numStats = statInfo.length;
		// numRows is: ceiling of numStats / numCols
		int numRows = numStats / numCols + ((numStats % numCols == 0) ? 0 : 1);
		// lastTall is: (numStats-1) mod numCols
		int lastTall = ((numStats - 1 + numCols) % numCols);
		rm2cm = new int[numStats]; // position i in row major order is position rm2cm[i] in column major
		cm2rm = new int[numStats]; // position j in column major order is position cm2rm[j] in row major
		for (int i = 0; i < numStats; i++) {
			int row = i / numCols;
			int col = i % numCols;
			int j = row + col * numRows
					- (col - 1 > lastTall ? col - 1 - lastTall : 0);
			rm2cm[i] = j;
			cm2rm[j] = i;
		}
	}

	/** a JPanel with one chart that plots a statistic vs. time, either all history or recent */
	private class Chart extends JPanel {
		/** for serializing */
		private static final long serialVersionUID = 1L;
		/** String array: {name, description, format} */
		private String[] statInfo;
		/** the index used to retrieve this statistic from Statistics */
		private int statIndex;
		/** is this all of history (as opposed to only recent)? */
		private boolean allHistory;
		/** the number of labels to generate */
		private long numSteps;

		int stringHeight;
		int stringHeightNoDesc;
		int minXs;
		int maxXs;
		int minYs;
		int maxYs;
		double minXb;
		double maxXb;
		double minYb;
		double maxYb;

		/**
		 * A chart that displays the history of a statistic from a StatsBuffer
		 *
		 * @param width
		 * 		width of the panel in pixels
		 * @param height
		 * 		height of the panel in pixels
		 * @param allHistory
		 * 		is this all of history (as opposed to only recent)?
		 * @param statIndex
		 * 		the index used to retrieve this statistic from Statistics
		 * @param statInfo
		 * 		String array: {name, description, format}
		 */
		Chart(int width, int height, boolean allHistory, int statIndex,
				String[] statInfo) {
			this.setPreferredSize(new Dimension(width, height));
			this.allHistory = allHistory;
			this.statIndex = statIndex;
			this.statInfo = statInfo;
			this.setBackground(Color.WHITE);
		}

		/**
		 * translate an x value from the buffer to screen coordinates, clipping it to lie inside the chart.
		 *
		 * @param x
		 * 		the x parameter from the buffer
		 * @return the x screen coordinate
		 */
		private int scaleX(double x) {
			if (minXb == maxXb) { // if only one data point,
				return (minXs + maxXs) / 2; // then display it in the middle
			}
			return (int) (//
					Math.min(1, Math.max(0, //
							(x - minXb) / (maxXb - minXb))) //
							* (maxXs - minXs) + minXs);
		}

		/**
		 * translate a y value from the buffer to screen coordinates, clipping it to lie inside the chart.
		 *
		 * @param y
		 * 		the y parameter from the buffer
		 * @return the y screen coordinate
		 */
		private int scaleY(double y) {
			if (minYb == maxYb) {// if a value has been constant throughout history,
				return (minYs + maxYs) / 2; // then display it in the middle
			}
			return (int) (//
					maxYs - Math.min(1, Math.max(0, //
							(y - minYb) / (maxYb - minYb))) //
							* (maxYs - minYs));
		}

		/**
		 * all x axis labels reflect x values divided by this. This is 1 for seconds, 60 for minutes, etc.
		 */
		double xDivisor = 1;
		/**
		 * the units for the x axis labels. If "seconds", then xDivisor is 1, if "minutes", then 60, etc.
		 */
		String xUnits = "seconds";

		/**
		 * Check if "step" is a good step size. If so, set numSteps to the number of labels it will
		 * generate, and return true. Else return false.
		 *
		 * @param step
		 * 		the proposed step size
		 * @param min
		 * 		the minimum value of all the data
		 * @param max
		 * 		the maximum value of all the data
		 * @return true if this step size won't produce too many labels
		 */
		private boolean good(double step, double min, double max) {
			if (step <= 0 || max < min) {
				return false;
			}
			numSteps = 1 + Math
					.round(Math.ceil(max / step) - Math.floor(min / step));
			return 4 <= numSteps && numSteps <= 7;
		}

		/**
		 * Create the y labels and choose their positions
		 *
		 * This method "returns" xLabel and xLabelVal, by changing those class variables.
		 */
		private void findYLabel() {
			if (maxYb == minYb) {
				String s = String.format("%,.0f", minYb);
				yLabel = new String[] { s };
				yLabelVal = new double[] { minYb };
				return;
			}
			double step10 = Math.pow(10,
					Math.floor(Math.log10(maxYb - minYb)) - 1);
			double step = 1;
			for (int i = 0; i < 4; i++) { // find a step that results in a good number of labels
				if (good(step = step10, minYb, maxYb)) {
					break;
				}
				if (good(step = step10 * 2, minYb, maxYb)) {
					break;
				}
				if (good(step = step10 * 5, minYb, maxYb)) {
					break;
				}
				step10 *= 10;
			}
			// number of decimal places to show, for the given step (0 if showing an integer is ok)
			long decimals = Math
					.round(Math.max(0, -Math.floor(Math.log10(step))));
			double epsilon = Math.pow(10, -1 - decimals); // if showing 2 decimal places, this is 0.001
			double localMaxYb = Math.ceil(maxYb / step) * step + epsilon;
			yLabel = new String[(int) numSteps - 2];
			yLabelVal = new double[(int) numSteps - 2];
			for (int i = 0; i < numSteps - 2; i++) {
				yLabelVal[i] = localMaxYb - (i + 1) * step + epsilon;
				yLabel[i] = String.format("%,." + decimals + "f", yLabelVal[i]);
			}
		}

		/**
		 * Create the x labels and choose their positions
		 *
		 * This method "returns" xLabel and xLabelVal, by changing those class variables.
		 */
		private void findXLabel() {
			if (maxXb <= minXb) {
				String s = String.format("%,.0f", minXb);
				xLabel = new String[] { s };
				xLabelVal = new double[] { minXb };
				return;
			}
			double step10 = Math.pow(10,
					Math.floor(Math.log10(maxXb - minXb)) - 1);
			double step = 1;
			for (int i = 0; i < 4; i++) { // find a step that results in a good number of labels
				if (good(step = step10, minXb, maxXb)) {
					break;
				}
				if (good(step = step10 * 2, minXb, maxXb)) {
					break;
				}
				if (good(step = step10 * 5, minXb, maxXb)) {
					break;
				}
				step10 *= 10;
			}
			if (!good(step, minXb, maxXb)) {
				// couldn't find a good step size. Maybe numSteps or the data are NaN?
				numSteps = 4;
				step = 1;
			}
			// number of decimal places to show, for the given step (0 if showing an integer is ok)
			long decimals = Math
					.round(Math.max(0, -Math.floor(Math.log10(step))));
			double epsilon = Math.pow(10, -1 - decimals); // if showing 2 decimal places, this is 0.001
			double localMaxXb = Math.ceil(maxXb / step) * step + epsilon;
			xLabel = new String[(int) numSteps - 2];
			xLabelVal = new double[(int) numSteps - 2];
			for (int i = 0; i < numSteps - 2; i++) {
				xLabelVal[i] = localMaxXb - (i + 1) * step + epsilon;
				xLabel[i] = String.format("%,." + decimals + "f", xLabelVal[i]);
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			try {
				StatsBuffer buffer;
				if (allHistory) {
					buffer = WinBrowser.memberDisplayed.platform.getStats()
							.getAllHistory(statIndex);
				} else {
					buffer = WinBrowser.memberDisplayed.platform.getStats()
							.getRecentHistory(statIndex);
				}
				minXs = 120;
				maxXs = getWidth() - 55;
				minYs = 40;
				maxYs = getHeight() - 50;

				minXb = buffer.xMin();
				maxXb = buffer.xMax();
				minYb = buffer.yMin();
				maxYb = buffer.yMax();

				if (maxXb - minXb > 60 * 60 * 24 * 365) {
					xDivisor = 60 * 60 * 24 * 365;
					xUnits = "years";
				} else if (maxXb - minXb > 60 * 60 * 24) {
					xDivisor = 60 * 60 * 24;
					xUnits = "days";
				} else if (maxXb - minXb > 60 * 60) {
					xDivisor = 60 * 60;
					xUnits = "hours";
				} else if (maxXb - minXb > 60 * 1.1) {
					xDivisor = 60;
					xUnits = "minutes";
				} else {
					xDivisor = 1;
					xUnits = "seconds";
				}

				g.setColor(Color.BLACK);
				stringHeight = g.getFontMetrics().getHeight();
				stringHeightNoDesc = g.getFontMetrics().getHeight()
						- g.getFontMetrics().getDescent();
				String title = statInfo[0] + " vs. time for "
						+ (allHistory ? "all" : "recent") + " history";

				if (buffer.numBins() == 0) {
					String s = String.format(
							"Skipping the first %,.0f seconds ...",
							Settings.statsSkipSeconds);
					int w = g.getFontMetrics().stringWidth(s);
					g.drawString(s, (minXs + maxXs - w) / 2,
							(minYs + maxYs) / 2);
					g.drawLine(minXs, maxYs, maxXs, maxYs); // x axis
					g.drawLine(minXs, minYs, minXs, maxYs); // y axis
				}
				for (int i = 0; i < buffer.numBins(); i++) {
					g.setColor(LIGHT_GRAY);
					int x0, y0, x1, y1;
					x0 = scaleX(i == 0 ? buffer.xMin(i) : buffer.xMax(i - 1)); // eliminate gaps
					y0 = scaleY(buffer.yMax(i));
					x1 = scaleX(buffer.xMax(i));
					y1 = scaleY(buffer.yMin(i));
					g.fillRect(x0, y0, x1 - x0, y1 - y0);
					g.setColor(DARK_GRAY);
					x0 = scaleX(i == 0 ? buffer.xMin(i) : buffer.xMax(i - 1)); // eliminate gaps
					y0 = scaleY(buffer.yAvg(i) + buffer.yStd(i));
					x1 = scaleX(buffer.xMax(i));
					y1 = scaleY(buffer.yAvg(i) - buffer.yStd(i));
					g.fillRect(x0, y0, x1 - x0, y1 - y0);
					g.setColor(Color.BLUE);
					if (i > 0) {
						x0 = scaleX(buffer.xAvg(i - 1));
						y0 = scaleY(buffer.yAvg(i - 1));
						x1 = scaleX(buffer.xAvg(i));
						y1 = scaleY(buffer.yAvg(i));
						g.drawLine(x0, y0, x1, y1);
					}
				}
				g.setColor(Color.BLACK);

				// draw title and x axis name:

				int w = g.getFontMetrics().stringWidth(title);
				g.drawString(title, minXs + (maxXs - minXs - w) / 2,
						stringHeight);
				String s = "Time in the past (in " + xUnits + ")";
				w = g.getFontMetrics().stringWidth(s);
				g.drawString(s, minXs + (maxXs - minXs - w) / 2,
						getHeight() - stringHeight);

				if (buffer.numBins() == 0) {
					return; // don't draw labels when there's no data
				}

				// draw X axis labels:

				double tt = minXb;
				minXb = (minXb - tt) / xDivisor;
				maxXb = (maxXb - tt) / xDivisor;
				double t2 = (maxXb - minXb) / 50;
				minXb -= t2; // make sure the 0 label is displayed
				findXLabel(); // labels reflect "time ago", scaled to current units
				minXb += t2;
				minXb = minXb * xDivisor + tt;
				maxXb = maxXb * xDivisor + tt;
				for (int i = 0; i < xLabelVal.length; i++) {
					xLabelVal[i] = xLabelVal[i] * xDivisor + tt;
				}

				for (int i = 0; i < xLabel.length; i++) {
					int x = maxXs - (scaleX(xLabelVal[i]) - minXs);
					int stringWidth = g.getFontMetrics().stringWidth(xLabel[i]);
					g.drawLine(x, maxYs + 4, x, maxYs - 4);
					g.drawString(xLabel[i], x - stringWidth / 2 + 1,
							maxYs + stringHeight + 5);
				}

				// draw Y axis labels:

				findYLabel();

				for (int i = 0; i < yLabel.length; i++) {
					int y = scaleY(yLabelVal[i]);
					int stringWidth = g.getFontMetrics().stringWidth(yLabel[i]);
					g.drawLine(minXs - 4, y, minXs + 4, y);
					g.drawString(yLabel[i], minXs - stringWidth - 10,
							y + stringHeightNoDesc / 2 - 1);
				}

				// draw X and Y axes

				g.drawLine(minXs, maxYs, maxXs, maxYs); // x axis
				g.drawLine(minXs, minYs, minXs, maxYs); // y axis
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(), "error while painting: {}",
						e);
			}
		}
	}

	/**
	 * make visible the chart with the given index, and turn its box blue. This is also the index of the
	 * statistic that it shows.
	 *
	 * @param index
	 * 		the index of the chart, which is also the index of the statistic
	 */
	void showChart(int index) {
		int j = cm2rm[index];
		chartsPanel.add(charts[index]);
		charts[index].setVisible(true); // flag this as having been added
		statBoxes[j].setBackground(WinBrowser.MEMBER_HIGHLIGHT_COLOR);
		revalidate();
	}

	/**
	 * make invisible the chart with the given index, and turn its box white. This is also the index of the
	 * statistic that it shows.
	 *
	 * @param index
	 * 		the index of the chart, which is also the index of the statistic
	 */
	void hideChart(int index) {
		int j = cm2rm[index];
		chartsPanel.remove(charts[index]);
		charts[index].setVisible(false); // flag this as having been removed
		statBoxes[j].setBackground(Color.WHITE);
		revalidate();
	}

	void prePaint() {
		if (WinBrowser.memberDisplayed == null) {
			return; // the screen is blank until we choose who to display
		}

		if (statBoxes != null) {
			// set up every Component on the screen, but only once
			return;
		}
		Statistics stats = WinBrowser.memberDisplayed.platform.getStats();
		boxesPanel = new JPanel(new GridBagLayout());
		chartsPanel = new JPanel();
		BoxLayout boxLayout = new BoxLayout(chartsPanel, BoxLayout.Y_AXIS);
		chartsPanel.setLayout(boxLayout);

		setLayout(new GridBagLayout());
		boxesPanel.setBackground(Color.WHITE);
		chartsPanel.setBackground(Color.WHITE);
		setBackground(Color.WHITE);

		JTextArea instructions = WinBrowser.newJTextArea();
		JTextArea spacer = WinBrowser.newJTextArea();
		instructions.setText(Utilities.wrap(50, ""//
				+ "The table shows various statistics about how the sytem is running. "//
				+ "Click on a white square to plot the history of the statistic. "//
				+ "Click on a highlighted square to hide that plot. \n\n" //
				+ "Each plot shows history divided into periods. Within one period, the blue line goes through "//
				+ "the average value during that period. The light gray box shows the min and max value during "//
				+ "that period. The dark gray box shows one standard deviation above and below the mean."));

		statInfo = stats.getAvailableStats();
		final int numStats = statInfo.length;
		statBoxes = new JLabel[numStats];
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.anchor = GridBagConstraints.FIRST_LINE_START; // grid (0,0) is upper-left corner
		c.gridx = 0;
		c.gridy = 0;
		charts = new JPanel[numStats];
		for (int i = 0; i < numStats; i++) { // i goes through them in row major order
			int j = rm2cm[i]; // j goes through them in column major order
			JLabel txt = new JLabel();
			txt.setToolTipText(statInfo[j][1]);
			txt.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(Color.BLACK),
					BorderFactory.createEmptyBorder(5, 5, 5, 5)));
			statBoxes[i] = txt;

			c.gridx = i % numCols;
			c.gridy = i / numCols;
			c.gridwidth = 1; // not end of row
			c.weightx = 0;
			boxesPanel.add(txt, c);
			if (i % numCols == numCols - 1) {
				JTextArea spacer2 = WinBrowser.newJTextArea();
				c.gridx++;
				c.weightx = 1.0f;
				c.gridwidth = GridBagConstraints.REMAINDER; // end of row
				boxesPanel.add(spacer2, c);
			}
			if (stats.getAllHistory(j) == null) {// if no history, then box is gray, and not clickable
				statBoxes[i].setBackground(LIGHT_GRAY);
				statBoxes[i].setOpaque(true);
			} else { // else history exists, so white and can draw chart
				statBoxes[i].setBackground(Color.WHITE);
				statBoxes[i].setOpaque(true);
				charts[j] = new JPanel();
				charts[j].setBackground(Color.WHITE);
				charts[j].add(new Chart(750, 400, false, j, statInfo[j]));
				charts[j].add(new Chart(750, 400, true, j, statInfo[j]));
				charts[j].setVisible(false); // use "visible" as a flag to indicate it's in the container
				final int jj = j; // effectively final so the listener can use it
				statBoxes[i].addMouseListener(new MouseAdapter() {
					public void mousePressed(MouseEvent e) {
						if (charts[jj].isVisible()) {
							hideChart(jj);
						} else {
							showChart(jj);
						}
					}
				});
			}
		}
		showChart(WinBrowser.memberDisplayed.platform.getStats()
				.getStatIndex("trans/sec")); // or could do "events/sec"
		descriptions = WinBrowser.newJTextArea();
		descriptions.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
		String s = "";
		for (int j = 0; j < statBoxes.length; j++) {
			s += String.format("%17s: %s\n", statInfo[j][0], statInfo[j][1]);
		}
		descriptions.setText(s);
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.insets = new Insets(10, 10, 10, 10);
		add(boxesPanel, c);
		c.gridy = 1;
		add(spacer, c);
		c.gridwidth = GridBagConstraints.REMAINDER; // end of row
		c.gridx = 1;
		c.gridheight = 2;
		c.gridy = 0;
		add(instructions, c);
		c.gridx = 0;
		c.gridy = 2;
		c.gridheight = 1;
		add(chartsPanel, c);
		c.gridy = 3;
		add(descriptions, c);
		JTextArea spacer2 = WinBrowser.newJTextArea();
		c.gridy = 4;
		c.weighty = 1.0f;
		add(spacer2, c);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (WinBrowser.memberDisplayed == null) {
			return; // the screen is blank until we choose who to display
		}
		Statistics stats = WinBrowser.memberDisplayed.platform.getStats();
		for (int i = 0; i < statBoxes.length; i++) {
			int j = rm2cm[i];
			statBoxes[i].setForeground(Color.BLACK);
			statBoxes[i].setText("<html><center>&nbsp;&nbsp;&nbsp;&nbsp;"
					+ statInfo[j][0] + "&nbsp;&nbsp;&nbsp;&nbsp;<br>"
					+ stats.getStatString(j).trim() + "</center></html>");
			statBoxes[i].setHorizontalAlignment(JLabel.CENTER);
		}
	}
}
