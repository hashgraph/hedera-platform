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
package com.swirlds.common;

import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.KeyListener;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * A console window. It is similar to the one written to by {@link System#out}. The window has the
 * size/location recommended by the browser, and contains scrolling white text on a black background. If
 * {@code cons} is a {@link Console}, then it can be written to with {@code cons.out.print()} and
 * <code>cons.out.println()</code> in the same way as the System <code>print()</code> and
 * <code>println()</code> write to the Java console.
 */
public class Console extends JFrame {
	/**
	 * Use {@link #out} to write to this console window in the same way that {@link System#out} is used to
	 * write to Java's console window.
	 */
	public final PrintStream out;
	/** the window holding the console */
	private JFrame window = null;
	/** the heading text at the top */
	private JTextArea heading = null;
	/** the main body text below the heading */
	private JTextArea textArea = null;
	/** the scroll pane containing textArea and heading */
	private JScrollPane scrollPane = null;
	/** max number of characters stored stored. Older text is deleted. */
	private final int maxSize = 50 * 1024;
	/**
	 *
	 */
	private ConsoleStream consoleStream;

	private class ConsoleStream extends ByteArrayOutputStream {
		@Override
		synchronized public void flush() {
			String str = toString();
			reset();
			if (str.equals("")) {
				return;
			}
			str = textArea.getText() + str;
			int n = (int) (0.75 * maxSize);
			if (str.length() > n) {
				int i = str.lastIndexOf("\r", str.length() - n);
				i = Math.max(i, str.lastIndexOf("\n", str.length() - n));
				i = Math.max(i, 0);
				i = Math.max(i, (int) (str.length() - maxSize));
				str = str.substring(i);
			}
			textArea.setText(str);

			JScrollBar bar = scrollPane.getVerticalScrollBar();
			// the position of a scrollbar handle (its value) is at most maxScroll.
			// this could have used textArea.getHeight() instead of bar.getMaximum().
			int maxScroll = bar.getMaximum() - bar.getModel().getExtent();
			// If the user scrolls to the bottom (or near it), then be sticky, and
			// stay at the exact bottom.
			if (maxScroll - bar.getValue() < .1 * bar.getMaximum()) {
				bar.setValue(maxScroll);
			}
		}
	}

	/**
	 * Get the number of rows of text visible, given the current size of the window.
	 *
	 * @return the number of rows visible
	 */
	public int getNumRows() {
		int lineHeight = textArea.getFontMetrics(textArea.getFont())
				.getHeight();
		Rectangle viewRect = textArea.getVisibleRect();
		int linesVisible = viewRect.height / lineHeight;
		return linesVisible;
	}

	public Console(String name, int bufferSize, Rectangle winRect, int fontSize,
			boolean visible) {
		if (GraphicsEnvironment.isHeadless()) {
			out = null;
		} else {
			heading = new JTextArea(2, 40);
			heading.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
			heading.setEditable(false);
			heading.setBackground(Color.BLACK);
			heading.setForeground(Color.WHITE);

			textArea = new JTextArea(10, 40);
			textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
			textArea.setEditable(false);
			textArea.setBackground(Color.BLACK);
			textArea.setForeground(Color.WHITE);

			scrollPane = new JScrollPane();
			scrollPane.setViewportView(textArea);
			scrollPane.setColumnHeaderView(heading);
			scrollPane.setBackground(Color.BLACK);

			window = new JFrame(name); // create a new window
			window.setBackground(Color.BLACK);
			window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			window.setBackground(Color.DARK_GRAY);
			window.add(scrollPane, BorderLayout.CENTER);
			window.setFocusable(true);
			window.requestFocusInWindow();
			window.setSize(winRect.width, winRect.height);
			window.setLocation(winRect.x, winRect.y);
			window.setVisible(visible);

			consoleStream = new ConsoleStream();
			out = new PrintStream(consoleStream, true);
		}
	}

	/**
	 * put the given text at the top of the console, above the region that scrolls
	 *
	 * @param headingText
	 * 		the text to display at the top
	 */
	public void setHeading(String headingText) {
		heading.setText(headingText);
		heading.revalidate();
	}

	/**
	 * Adds the specified key listener to receive key events from this console.
	 *
	 * @param listener
	 * 		the key listener.
	 */
	public synchronized void addKeyListener(KeyListener listener) {
		window.addKeyListener(listener);
		heading.addKeyListener(listener);
		scrollPane.addKeyListener(listener);
		textArea.addKeyListener(listener);
	}

	/**
	 * set if the window holding console is visible
	 *
	 * @param visible
	 * 		whether the window holding console is visible
	 */
	public void setVisible(boolean visible) {
		window.setVisible(visible);
	}

	/**
	 * get window holding the console
	 *
	 * @return window holding console
	 */
	public JFrame getWindow() {
		return window;
	}
}
