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

package com.swirlds.blob.internal.db;

import java.util.LinkedList;
import java.util.List;

/**
 * Utility class which can tokenize a String into a list of String arguments,
 * with behavior similar to parsing command line arguments to a program.
 * Quoted Strings are treated as single arguments, and escaped characters
 * are translated so that the tokenized arguments have the same meaning.
 * Since all methods are static, the class is declared abstract to prevent
 * instantiation.
 *
 * @version $Id$
 */
public abstract class ArgumentTokenizer {
	private static final int NO_TOKEN_STATE = 0;
	private static final int NORMAL_TOKEN_STATE = 1;
	private static final int SINGLE_QUOTE_STATE = 2;
	private static final int DOUBLE_QUOTE_STATE = 3;

	/**
	 * Tokenizes the given String into String tokens
	 *
	 * @param arguments
	 * 		A String containing one or more command-line style arguments to be tokenized.
	 * @return A list of parsed and properly escaped arguments.
	 */
	public static List<String> tokenize(String arguments) {
		return tokenize(arguments, false);
	}

	/**
	 * Tokenizes the given String into String tokens.
	 *
	 * @param arguments
	 * 		A String containing one or more command-line style arguments to be tokenized.
	 * @param stringify
	 * 		whether or not to include escape special characters
	 * @return A list of parsed and properly escaped arguments.
	 */
	public static List<String> tokenize(String arguments, boolean stringify) {

		LinkedList<String> argList = new LinkedList<String>();
		StringBuilder currArg = new StringBuilder();
		boolean escaped = false;
		int state = NO_TOKEN_STATE;  // start in the NO_TOKEN_STATE
		int len = arguments.length();

		// Loop over each character in the string
		for (int i = 0; i < len; i++) {
			char c = arguments.charAt(i);
			if (escaped) {
				// Escaped state: just append the next character to the current arg.
				escaped = false;
				currArg.append(c);
			} else {
				switch (state) {
					case SINGLE_QUOTE_STATE:
						if (c == '\'') {
							// Seen the close quote; continue this arg until whitespace is seen
							state = NORMAL_TOKEN_STATE;
						} else {
							currArg.append(c);
						}
						break;
					case DOUBLE_QUOTE_STATE:
						if (c == '"') {
							// Seen the close quote; continue this arg until whitespace is seen
							state = NORMAL_TOKEN_STATE;
						} else if (c == '\\') {
							// Look ahead, and only escape quotes or backslashes
							i++;
							char next = arguments.charAt(i);
							if (next == '"' || next == '\\') {
								currArg.append(next);
							} else {
								currArg.append(c);
								currArg.append(next);
							}
						} else {
							currArg.append(c);
						}
						break;
//          case NORMAL_TOKEN_STATE:
//            if (Character.isWhitespace(c)) {
//              // Whitespace ends the token; start a new one
//              argList.add(currArg.toString());
//              currArg = new StringBuffer();
//              state = NO_TOKEN_STATE;
//            }
//            else if (c == '\\') {
//              // Backslash in a normal token: escape the next character
//              escaped = true;
//            }
//            else if (c == '\'') {
//              state = SINGLE_QUOTE_STATE;
//            }
//            else if (c == '"') {
//              state = DOUBLE_QUOTE_STATE;
//            }
//            else {
//              currArg.append(c);
//            }
//            break;
					case NO_TOKEN_STATE:
					case NORMAL_TOKEN_STATE:
						switch (c) {
							case '\\':
								escaped = true;
								state = NORMAL_TOKEN_STATE;
								break;
							case '\'':
								state = SINGLE_QUOTE_STATE;
								break;
							case '"':
								state = DOUBLE_QUOTE_STATE;
								break;
							default:
								if (!Character.isWhitespace(c)) {
									currArg.append(c);
									state = NORMAL_TOKEN_STATE;
								} else if (state == NORMAL_TOKEN_STATE) {
									// Whitespace ends the token; start a new one
									argList.add(currArg.toString());
									currArg = new StringBuilder();
									state = NO_TOKEN_STATE;
								}
						}
						break;
					default:
						throw new IllegalStateException("ArgumentTokenizer state " + state + " is invalid!");
				}
			}
		}

		// If we're still escaped, put in the backslash
		if (escaped) {
			currArg.append('\\');
			argList.add(currArg.toString());
		}
		// Close the last argument if we haven't yet
		else if (state != NO_TOKEN_STATE) {
			argList.add(currArg.toString());
		}
		// Format each argument if we've been told to stringify them
		if (stringify) {
			for (int i = 0; i < argList.size(); i++) {
				argList.set(i, "\"" + _escapeQuotesAndBackslashes(argList.get(i)) + "\"");
			}
		}
		return argList;
	}

	/**
	 * Inserts backslashes before any occurrences of a backslash or
	 * quote in the given string.  Also converts any special characters
	 * appropriately.
	 */
	protected static String _escapeQuotesAndBackslashes(String s) {
		final StringBuilder buf = new StringBuilder(s);

		// Walk backwards, looking for quotes or backslashes.
		//  If we see any, insert an extra backslash into the buffer at
		//  the same index.  (By walking backwards, the index into the buffer
		//  will remain correct as we change the buffer.)
		for (int i = s.length() - 1; i >= 0; i--) {
			char c = s.charAt(i);
			if ((c == '\\') || (c == '"')) {
				buf.insert(i, '\\');
			}
			// Replace any special characters with escaped versions
			else if (c == '\n') {
				buf.deleteCharAt(i);
				buf.insert(i, "\\n");
			} else if (c == '\t') {
				buf.deleteCharAt(i);
				buf.insert(i, "\\t");
			} else if (c == '\r') {
				buf.deleteCharAt(i);
				buf.insert(i, "\\r");
			} else if (c == '\b') {
				buf.deleteCharAt(i);
				buf.insert(i, "\\b");
			} else if (c == '\f') {
				buf.deleteCharAt(i);
				buf.insert(i, "\\f");
			}
		}
		return buf.toString();
	}
}
