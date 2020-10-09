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

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Utility class for other operations
 */
public class CommonUtils {
	/** the default charset used by swirlds */
	private static Charset defaultCharset = StandardCharsets.UTF_8;

	/** used by beep() */
	private static Synthesizer synthesizer;

	/** used by click(). It is opened and never closed. */
	private static Clip clip = null;
	/** used by click() */
	private static byte[] data = null;
	/** used by click() */
	private static AudioFormat format = null;

	/**
	 * Normalizes the string in accordance with the Swirlds default normalization method (NFD) and returns
	 * the bytes of that normalized String encoded in the Swirlds default charset (UTF8). This is important
	 * for having a consistent method of converting Strings to bytes that will guarantee that two identical
	 * strings will have an identical byte representation
	 *
	 * @param s
	 * 		the String to be converted to bytes
	 * @return a byte representation of the String
	 */
	public static byte[] getNormalisedStringBytes(String s) {
		if (s == null) {
			return null;
		}
		return Normalizer.normalize(s, Normalizer.Form.NFD).getBytes(defaultCharset);
	}

	/**
	 * Reverse of {@link #getNormalisedStringBytes(String)}
	 *
	 * @param bytes
	 * 		the bytes to convert
	 * @return a String created from the input bytes
	 */
	public static String getNormalisedStringFromBytes(byte[] bytes) {
		return new String(bytes, defaultCharset);
	}

	/**
	 * Play a beep sound. It is middle C, half volume, 20 milliseconds.
	 */
	public static void beep() {
		beep(60, 64, 20);
	}

	/**
	 * Make a beep sound.
	 *
	 * @param pitch
	 * 		the pitch, from 0 to 127, where 60 is middle C, 61 is C#, etc.
	 * @param velocity
	 * 		the "velocity" (volume, or speed with which the note is played). 0 is silent, 127 is max.
	 * @param duration
	 * 		the number of milliseconds the sound will play
	 */
	public static void beep(int pitch, int velocity, int duration) {
		try {
			if (synthesizer == null) {
				synthesizer = MidiSystem.getSynthesizer();
				synthesizer.open();
			}

			MidiChannel[] channels = synthesizer.getChannels();

			channels[0].noteOn(pitch, velocity);
			Thread.sleep(duration);
			channels[0].noteOff(60);
		} catch (Exception e) {
		}
	}

	/**
	 * Make a click sound.
	 */
	public static void click() {
		try {
			if (data == null) {
				data = new byte[] { 0, 127 };
				format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
						44100.0f, 16, 1, 2, 44100.0f, false);
				clip = AudioSystem.getClip();
				clip.open(format, data, 0, data.length);
			}
			clip.start(); // play the waveform in data
			while (clip.getFramePosition() < clip.getFrameLength()) {
				Thread.yield(); // busy wait, but it's only for a short time, and at least it yields
			}
			clip.stop(); // it should have already stopped
			clip.setFramePosition(0); // for next time, start over
		} catch (Exception e) {
		}
	}


	/**
	 * This is equivalent to System.out.println(), but is not used for debugging; it is used for production
	 * code for communicating to the user. Centralizing it here makes it easier to search for debug prints
	 * that might have slipped through before a release.
	 *
	 * @param msg
	 * 		the message for the user
	 */
	public static void tellUserConsole(String msg) {
		System.out.println(msg);
	}

	/**
	 * This is equivalent to sending text to doing both Utilities.tellUserConsole() and writing to a popup
	 * window. It is not used for debugging; it is used for production code for communicating to the user.
	 *
	 * @param title
	 * 		the title of the window to pop up
	 * @param msg
	 * 		the message for the user
	 */
	public static void tellUserConsolePopup(String title, String msg) {
		tellUserConsole("\n***** " + msg + " *****\n");
		if (!GraphicsEnvironment.isHeadless()) {
			String[] ss = msg.split("\n");
			int w = 0;
			for (String str : ss) {
				w = Math.max(w, str.length());
			}
			JTextArea ta = new JTextArea(ss.length + 1, (int) (w * 0.65));
			ta.setText(msg);
			ta.setWrapStyleWord(true);
			ta.setLineWrap(true);
			ta.setCaretPosition(0);
			ta.setEditable(false);
			ta.addHierarchyListener(new HierarchyListener() { // make ta resizable
				public void hierarchyChanged(HierarchyEvent e) {
					Window window = SwingUtilities.getWindowAncestor(ta);
					if (window instanceof Dialog) {
						Dialog dialog = (Dialog) window;
						if (!dialog.isResizable()) {
							dialog.setResizable(true);
						}
					}
				}
			});
			JScrollPane sp = new JScrollPane(ta);
			JOptionPane.showMessageDialog(null, sp, title,
					JOptionPane.PLAIN_MESSAGE);
		}
	}

	/**
	 * Converts a single byte to a lowercase hexadecimal string.
	 *
	 * @param b
	 * 		the byte to convert to hexadecimal
	 * @return a {@link String} containing the lowercase hexadecimal representation of the byte
	 */
	public static String hex(byte b) {
		return String.format("%02x", b);
	}

	/**
	 * Converts an array of bytes to a lowercase hexadecimal string.
	 *
	 * @param bytes
	 * 		the array of bytes to hexadecimal
	 * @param length
	 * 		the length of the array to convert to hex
	 * @return a {@link String} containing the lowercase hexadecimal representation of the byte array
	 */
	public static String hex(byte[] bytes, int length) {
		StringBuilder sb = new StringBuilder();

		if (bytes != null) {
			throwRangeInvalid("length", length, 0, bytes.length);
			for (int i = 0; i < length; i++) {
				sb.append(String.format("%02x", bytes[i]));
			}
			if (length < bytes.length) {
				sb.append("...");
			}
		} else {
			sb.append("null");
		}

		return sb.toString();
	}

	/**
	 * Equivalent to calling {@link #hex(byte[], int)} with length set to bytes.length
	 *
	 * @param bytes
	 * 		an array of bytes
	 * @return a {@link String} containing the lowercase hexadecimal representation of the byte array
	 */
	public static String hex(byte[] bytes) {
		return hex(bytes, bytes == null ? 0 : bytes.length);
	}

	/**
	 * Converts a hexadecimal string back to the original array of bytes.
	 *
	 * @param bytes
	 * 		the hexadecimal string to be converted
	 * @return an array of bytes
	 */
	public static byte[] unhex(CharSequence bytes) {

		if (bytes.length() % 2 != 0) {
			throw new IllegalArgumentException("bytes");
		}

		final int len = bytes.length();
		final byte[] data = new byte[(len / 2)];
		for (int i = 0; i < len; i += 2) {
			data[(i / 2)] = (byte) ((Character.digit(bytes.charAt(i), 16) << 4)
					+ Character.digit(bytes.charAt(i + 1), 16));
		}

		return data;
	}

	/**
	 * Throw an {@link IllegalArgumentException} if the supplied argument is null.
	 *
	 * @param arg
	 * 		the argument checked
	 * @param argName
	 * 		the name of the argument
	 */
	public static void throwArgNull(Object arg, String argName) {
		if (arg == null) {
			throw new IllegalArgumentException(
					String.format(
							"The supplied argument '%s' cannot be null!",
							argName
					)
			);
		}
	}

	/**
	 * Throws an exception if the value is outside of the specified range
	 *
	 * @param name
	 * 		the name of the variable
	 * @param value
	 * 		the value to check
	 * @param minValue
	 * 		the minimum allowed value
	 * @param maxValue
	 * 		the maximum allowed value
	 */
	public static void throwRangeInvalid(String name, int value, int minValue, int maxValue) {
		if (value < minValue || value > maxValue) {
			throw new IllegalArgumentException(
					String.format(
							"The argument '%s' should have a value between %d and %d! Value provided is %d",
							name,
							minValue,
							maxValue,
							value
					)
			);
		}
	}

	/**
	 * Given a sequence of directory and file names, such as {".","sdk","test","..","config.txt"}, convert
	 * it to a File in canonical form, such as /full/path/sdk/config.txt by assuming it starts in the
	 * current working directory (which is the same as System.getProperty("user.dir")).
	 *
	 * @param names
	 * 		the sequence of names
	 * @return the File in canonical form
	 */
	public static File canonicalFile(String... names) {
		return canonicalFile(new File("."), names);
	}

	/**
	 * Given a starting directory and a sequence of directory and file names, such as "sdk" and
	 * {"data","test","..","config.txt"}, convert it to a File in canonical form, such as
	 * /full/path/sdk/data/config.txt by assuming it starts in the current working directory (which is the
	 * same as System.getProperty("user.dir")).
	 *
	 * @param start
	 * 		the starting directory
	 * @param names
	 * 		the sequence of names
	 * @return the File in canonical form, or null if there are any errors
	 */
	public static File canonicalFile(File start, String... names) {
		File f = start;
		try {
			f = f.getCanonicalFile();
			for (int i = 0; i < names.length; i++) {
				f = new File(f, names[i]).getCanonicalFile();
			}
		} catch (IOException e) {
			f = null;
		}
		return f;
	}

	/**
	 * Given a name from the address book, return the corresponding alias to associate with certificates in
	 * the trust store. This is found by lowercasing all the letters, removing accents, and deleting every
	 * character other than letters and digits. A "letter" is anything in the Unicode category "letter",
	 * which includes most alphabets, as well as ideographs such as Chinese.
	 * <p>
	 * WARNING: Some versions of Java 8 have a terrible bug where even a single capital letter in an alias
	 * will prevent SSL or TLS connections from working (even though those protocols don't use the aliases).
	 * Although this ought to work fine with Chinese/Greek/Cyrillic characters, it is safer to stick with
	 * only the 26 English letters.
	 *
	 * @param name
	 * 		a name from the address book
	 * @return the corresponding alias
	 */
	public static String nameToAlias(String name) {
		// Convert to lowercase. The ROOT locale should work with most non-english characters. Though there
		// can be surprises. For example, in Turkey, the capital I would convert in a Turkey-specific way to
		// a "lowercase I without a dot". But ROOT would simply convert it to a lowercase I.
		String alias = name.toLowerCase(Locale.ROOT);

		// Now find each character that is a single Unicode codepoint for an accented character, and convert
		// it to an expanded form consisting of the unmodified letter followed
		// by all its modifiers. So if "à" was encoded as U+00E0, it will be converted to U+0061 U++U0300.
		// This is necessary because Unicode normally allows that character to be encoded either way, and
		// they are normally treated as equivalent.
		alias = Normalizer.normalize(alias, Normalizer.Form.NFD);

		// Finally, delete the modifiers. So the expanded "à" (U+0061 U++U0300) will be converted to "a"
		// (U+0061). Also delete all spaces, punctuation, special characters, etc. Leave only digits and
		// unaccented letters. Specifically, leave only the 10 digits 0-9 and the characters that have a
		// Unicode category of "letter". Letters include alphabets (Latin, Cyrillic, etc.)
		// and ideographs (Chinese, etc.).
		alias = alias.replaceAll("[^\\p{L}0-9]", "");
		return alias;
	}

	/**
	 * Delete a directory and all files contained in this directory
	 *
	 * @param directoryToBeDeleted
	 * 		the directory to be deleted
	 * @return <code>true</code> if and only if the directory is
	 * 		successfully deleted; <code>false</code> otherwise
	 */
	public static boolean deleteDirectory(File directoryToBeDeleted) {
		File[] allContents = directoryToBeDeleted.listFiles();
		if (allContents != null) {
			for (File file : allContents) {
				deleteDirectory(file);
			}
		}
		return directoryToBeDeleted.delete();
	}
}
