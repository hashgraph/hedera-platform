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

import com.swirlds.platform.StateHierarchy.InfoApp;
import com.swirlds.platform.StateHierarchy.InfoMember;
import com.swirlds.platform.StateHierarchy.InfoSwirld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JPanel;
import javax.swing.JTextPane;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * The tab in the Browser window that shows available apps, running swirlds, and saved swirlds.
 */
class WinTabSwirlds extends WinBrowser.PrePaintableJPanel {
	private static final long serialVersionUID = 1L;
	private JTextPane lastText = null;
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	private JTextPane instructions;
	/** should the next prePaint rebuild the window contents? */
	private boolean redoWindow = true;

	public WinTabSwirlds() {
		GridBagLayout gridbag = new GridBagLayout();
		setLayout(gridbag);
		setFont(WinBrowser.FONT);
		chooseMemberDisplayed();

		instructions = new JTextPane();
		instructions.setText(Utilities.wrap(70, "\n\n" //
				+ "The above are all of the known apps in the data/apps directory. "
				+ "Under each app is all of the known swirlds (i.e., shared worlds, "
				+ "shared ledgers, shared databases). "
				+ "Under each swirld is all of the known members using this local machine. "
				+ "Each one that is currently active is marked as \"running\". "
				+ "Click on a member to show their information in all the tabs here. "
				+ "Minimize this window to hide it. Close it to quit the entire program."));
		freeze(instructions);
	}

	/** {@inheritDoc} */
	void prePaint() {
		if (!redoWindow) {
			return;
		}
		redoWindow = false;
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.gridwidth = GridBagConstraints.REMAINDER; // end row
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		c.gridx = 0;
		c.gridy = 0;
		for (InfoApp app : Browser.stateHierarchy.apps) {
			addEntity(this, app, c, 0, "", false);
			c.gridy++;
			for (InfoSwirld swirld : app.swirlds) {
				addEntity(this, swirld, c, 1, "", false);
				c.gridy++;
				for (InfoMember member : swirld.members) {
					if (WinBrowser.memberDisplayed == null) {
						setMemberDisplayed(member);
					}
					addEntity(this, member, c, 2,
							member.platform == null ? "" : " (running)    ",
							true);
					c.gridy++;
				}
			}
		}
		c.weightx = 0.0;
		c.weighty = 0.0;
		c.insets = new Insets(10, 10, 10, 10);
		add(instructions, c);
		c.gridy++;
		if (lastText == null) { // add an invisible spacer that takes up all extra space
			lastText = new JTextPane();
			lastText.setText("");
			freeze(lastText);
			c.weightx = 1.0;
			c.weighty = 1.0;
			add(lastText, c);
			c.gridy++;
		}
	}

	void setMemberDisplayed(StateHierarchy.InfoMember member) {
		WinBrowser.memberDisplayed = member;
		WinBrowser.nameBarName.setText("    " + member.name + "    ");
		for (InfoApp app : Browser.stateHierarchy.apps) {
			for (InfoSwirld swirld : app.swirlds) {
				for (InfoMember mem : swirld.members) {
					if (mem.panel != null) {
						((EntityRow) mem.panel)
								.setColor(WinBrowser.memberDisplayed == mem
										? WinBrowser.MEMBER_HIGHLIGHT_COLOR
										: Color.WHITE);
					}
				}
			}
		}
	}

	void addEntity(WinTabSwirlds parent, StateHierarchy.InfoEntity entity,
			GridBagConstraints c, int level, String suffix,
			boolean selectable) {
		if (entity.panel != null) { // ignore if it has already been added
			return;
		}
		EntityRow row = new EntityRow(entity, level, suffix, selectable);
		row.setColor((WinBrowser.memberDisplayed == entity)
				? WinBrowser.MEMBER_HIGHLIGHT_COLOR
				: Color.WHITE);
		add(row, c);
	}

	/** when clicked on, set the entity displayed to be this entity */
	private class ClickToSelect extends MouseAdapter {
		StateHierarchy.InfoMember member;

		ClickToSelect(StateHierarchy.InfoEntity member) {
			if (!(member instanceof StateHierarchy.InfoMember)) {
				// only entities representing a Member should be clickable to select as
				// WinBrowser.memberDisplayed
				log.error(EXCEPTION.getMarker(),
						"WinTabSwirlds.ClickToSelect instantiated with {} which is not an InfoMember",
						member);
				return;
			}
			this.member = (StateHierarchy.InfoMember) member;
		}

		public void mousePressed(MouseEvent mouseEvent) {
			if (mouseEvent.getButton() == MouseEvent.BUTTON1) {
				setMemberDisplayed(member);
			}
		}
	}

	/** a JPanel that represents one App, Swirld, or Member, which is one row in the Swirlds tab */
	class EntityRow extends JPanel {
		/** needed for serializing */
		private static final long serialVersionUID = 1L;
		/** the app, swirld, member, or state represented by this JPanel */
		StateHierarchy.InfoEntity member;
		/** amount to indent (0 = none) */
		int level;
		JPanel indent;
		JTextPane name;
		JTextPane suf;

		/** set the color of the row, name, and suffix. But leave the indentation white */
		void setColor(Color color) {
			this.setBackground(Color.WHITE);
			indent.setBackground(Color.WHITE);
			name.setBackground(color);
			suf.setBackground(color);
		}

		/**
		 * Create a JPanel representing the entity, and give the entity a reference to it.
		 *
		 * @param entity
		 * 		the entity to display
		 * @param level
		 * 		number of levels to indent (0 to not indent)
		 * @param suffix
		 * 		a string to add at the end of the panel (on the right)
		 * @param selectable
		 * 		true if this row can be selected as the member displayed
		 */
		EntityRow(StateHierarchy.InfoEntity entity, int level, String suffix,
				boolean selectable) {
			this.member = entity;
			this.level = level;
			indent = new JPanel();
			name = new JTextPane();
			suf = new JTextPane();

			if (selectable) {
				indent.addMouseListener(new ClickToSelect(entity));
				name.addMouseListener(new ClickToSelect(entity));
				suf.addMouseListener(new ClickToSelect(entity));
				addMouseListener(new ClickToSelect(entity));
			}

			this.setLayout(new FlowLayout(FlowLayout.LEADING, 0, 2));
			indent.setPreferredSize(new Dimension(30 * level, 10));
			indent.setMinimumSize(new Dimension(30 * level, 10));
			name.setText("    " + entity.name + "    ");
			freeze(name);
			freeze(suf);
			suf.setText(suffix);
			add(indent);
			add(name);
			add(suf);
			entity.panel = this;
		}
	}

	void freeze(JTextPane text) {
		text.setFont(WinBrowser.FONT);
		text.setEditable(false);
		text.setEnabled(false);
		text.setBackground(Color.WHITE);
		text.setForeground(Color.BLACK);
		text.setDisabledTextColor(Color.BLACK);
	}

	void chooseMemberDisplayed() {
		// If there is no name at the top of the browser window (above the tab buttons),
		// then put the first member there
		try {// if there is concurrent modification, then do nothing, and try again later
			for (InfoApp app : Browser.stateHierarchy.apps) {
				for (InfoSwirld swirld : app.swirlds) {
					for (InfoMember member : swirld.members) {
						if (WinBrowser.memberDisplayed == null) {
							this.setMemberDisplayed(member);
						}
					}
				}
			}
		} catch (Exception e) {
		}
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
	}
}
