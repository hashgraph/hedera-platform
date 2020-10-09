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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.DataStreamUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.platform.internal.Deserializer;
import com.swirlds.platform.internal.Serializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.net.SocketException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This is a collection of static utility methods, such as for comparing and deep cloning of arrays.
 */
public class Utilities extends DataStreamUtils {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/**
	 * Convert a string to a boolean.
	 *
	 * A false is defined to be any string that, after trimming leading/trailing whitespace and conversion
	 * to lowercase, is equal to null, or the empty string, or "off" or "0", or starts with "f" or "n". All
	 * other strings are true.
	 *
	 * @param par
	 * 		the string to convert (or null)
	 * @return the boolean value
	 */
	static boolean parseBoolean(String par) {
		if (par == null) {
			return false;
		}
		String p = par.trim().toLowerCase();
		if (p.equals("")) {
			return false;
		}
		String f = p.substring(0, 1);
		return !(p.equals("0") || f.equals("f") || f.equals("n")
				|| p.equals("off"));
	}

	/**
	 * Do a deep clone of a 2D array. Here, "deep" means that after doing x=deepClone(y), x won't be
	 * affected by changes to any part of y, such as assigning to y or to y[0] or to y[0][0].
	 *
	 * @param original
	 * 		the original array
	 * @return the deep clone
	 */
	public static long[][] deepClone(long[][] original) {
		if (original == null) {
			return null;
		}
		long[][] result = original.clone();
		for (int i = 0; i < original.length; i++) {
			if (original[i] != null) {
				result[i] = original[i].clone();
			}
		}
		return result;
	}

	/**
	 * Do a deep clone of a 2D array. Here, "deep" means that after doing x=deepClone(y), x won't be
	 * affected by changes to any part of y, such as assigning to y or to y[0] or to y[0][0].
	 *
	 * @param original
	 * 		the original array
	 * @return the deep clone
	 */
	public static byte[][] deepClone(byte[][] original) {
		if (original == null) {
			return null;
		}
		byte[][] result = original.clone();
		for (int i = 0; i < original.length; i++) {
			if (original[i] != null) {
				result[i] = original[i].clone();
			}
		}
		return result;
	}

	/**
	 * Do a deep clone of any serialiazable object that can reach only other serializable objects through
	 * following references.
	 *
	 * @param original
	 * 		the object to clone
	 * @return the clone
	 */
	public static Object deepCloneBySerializing(Object original) {
		ObjectOutputStream dos = null;
		ObjectInputStream dis = null;
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			dos = new ObjectOutputStream(bos);
			// serialize and pass the object
			dos.writeObject(original);
			dos.flush();
			ByteArrayInputStream bin = new ByteArrayInputStream(
					bos.toByteArray());
			dis = new ObjectInputStream(bin);
			return dis.readObject();
		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(), "", e);
		} finally {
			try {
				dos.close();
			} catch (Exception e) {
			}
			try {
				dis.close();
			} catch (Exception e) {
			}
		}
		return null;
	}

	/**
	 * Compare arrays lexicographically, with element 0 having the most influence.
	 * A null array is considered less than a non-null array.
	 * This is the same as Java.Util.Arrays#compar
	 *
	 * @param sig1
	 * 		first array
	 * @param sig2
	 * 		second array
	 * @return 1 if first is bigger, -1 if second, 0 otherwise
	 */
	static int arrayCompare(byte[] sig1, byte[] sig2) {
		if (sig1 == null && sig2 == null) {
			return 0;
		}
		if (sig1 == null && sig2 != null) {
			return -1;
		}
		if (sig1 != null && sig2 == null) {
			return 1;
		}
		for (int i = 0; i < Math.min(sig1.length, sig2.length); i++) {
			if (sig1[i] < sig2[i]) {
				return -1;
			}
			if (sig1[i] > sig2[i]) {
				return 1;
			}
		}
		if (sig1.length < sig2.length) {
			return -1;
		}
		if (sig1.length > sig2.length) {
			return 1;
		}
		return 0;
	}

	/**
	 * Compare arrays lexicographically, with element 0 having the most influence, as if each array was
	 * XORed with whitening before the comparison. The XOR doesn't actually happen, and the arrays are left
	 * unchanged.
	 *
	 * @param sig1
	 * 		first array
	 * @param sig2
	 * 		second array
	 * @param whitening
	 * 		the array virtually XORed with the other two
	 * @return 1 if first is bigger, -1 if second, 0 otherwise
	 */
	static int arrayCompare(byte[] sig1, byte[] sig2, byte[] whitening) {
		int maxLen, minLen;
		if (sig1 == null && sig2 == null) {
			return 0;
		}
		if (sig1 != null && sig2 == null) {
			return 1;
		}
		if (sig1 == null && sig2 != null) {
			return -1;
		}
		maxLen = Math.max(sig1.length, sig2.length);
		minLen = Math.min(sig1.length, sig2.length);
		if (whitening.length < maxLen) {
			whitening = Arrays.copyOf(whitening, maxLen);
		}
		for (int i = 0; i < minLen; i++) {
			int b1 = sig1[i] ^ whitening[i];
			int b2 = sig2[i] ^ whitening[i];
			if (b1 > b2) {
				return 1;
			}
			if (b1 < b2) {
				return -1;
			}
		}
		if (sig1.length > sig2.length) {
			return 1;
		}
		if (sig1.length < sig2.length) {
			return -1;
		}
		return 0;
	}

	/////////////////////////////////////////////////////////////
	// read from DataInputStream and
	// write to DataOutputStream

	/**
	 * write an Instant to the given stream
	 *
	 * @param stream
	 * 		the stream to write to
	 * @param instant
	 * 		the instant to write
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static void writeInstant(SerializableDataOutputStream stream, Instant instant)
			throws IOException {
		stream.writeLong(instant.getEpochSecond());
		stream.writeLong(instant.getNano());
	}

	/**
	 * read an Instant from the given stream
	 *
	 * @param stream
	 * 		the stream to read from
	 * @return the Instant that was read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static Instant readInstant(SerializableDataInputStream stream)
			throws IOException {
		Instant time = Instant.ofEpochSecond(//
				stream.readLong(), // from getEpochSecond()
				stream.readLong()); // from getNano()
		return time;
	}

	/**
	 * write an int array to the given stream
	 *
	 * @param stream
	 * 		the stream to write to
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static void writeIntArray(DataOutputStream stream, int[] data)
			throws IOException {
		int len = (data == null ? 0 : data.length);
		stream.writeInt(len);
		for (int i = 0; i < len; i++) {
			stream.writeInt(data[i]);
		}
	}

	/**
	 * read an int array from the given stream
	 *
	 * @param stream
	 * 		the stream to read from
	 * @return the array that was read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static int[] readIntArray(DataInputStream stream)
			throws IOException {
		int len = stream.readInt();
		int[] data = new int[len];
		for (int i = 0; i < len; i++) {
			data[i] = stream.readInt();
		}
		return data;
	}

	/**
	 * write a long array to the given stream
	 *
	 * @param stream
	 * 		the stream to write to
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static void writeLongArray(DataOutputStream stream, long[] data)
			throws IOException {
		int len = (data == null ? 0 : data.length);
		stream.writeInt(len);
		for (int i = 0; i < len; i++) {
			stream.writeLong(data[i]);
		}
	}

	/**
	 * read a long array from the given stream
	 *
	 * @param stream
	 * 		the stream to read from
	 * @return the array that was read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static long[] readLongArray(DataInputStream stream)
			throws IOException {
		int len = stream.readInt();
		long[] data = new long[len];
		for (int i = 0; i < len; i++) {
			data[i] = stream.readLong();
		}
		return data;
	}

	/**
	 * write a 2D long array to the given stream
	 *
	 * @param stream
	 * 		the stream to write to
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static void writeLongArray2D(SerializableDataOutputStream stream,
			long[][] data) throws IOException {
		int len = (data == null ? 0 : data.length);
		stream.writeInt(len);
		for (int i = 0; i < len; i++) {
			writeLongArray(stream, data[i]);
		}
	}

	/**
	 * read a 2D long array from the given stream
	 *
	 * @param stream
	 * 		the stream to read from
	 * @return the array that was read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static long[][] readLongArray2D(SerializableDataInputStream stream)
			throws IOException {
		int len = stream.readInt();
		long[][] data = new long[len][];
		for (int i = 0; i < len; i++) {
			data[i] = readLongArray(stream);
		}
		return data;
	}

	/**
	 * write a String array to the given stream
	 *
	 * @param stream
	 * 		the stream to write to
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static void writeStringArray(SerializableDataOutputStream stream,
			String[] data) throws IOException {
		int len = (data == null ? 0 : data.length);
		stream.writeInt(len);
		for (int i = 0; i < len; i++) {
			// we write a boolean before every array element to indicate if it's null or not
			if (data[i] != null) {
				stream.writeBoolean(true);
				stream.writeUTF(data[i]);
			} else {
				stream.writeBoolean(false);
			}

		}
	}

	/**
	 * read a String array from the given stream
	 *
	 * @param stream
	 * 		the stream to read from
	 * @return the array that was read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static String[] readStringArray(SerializableDataInputStream stream)
			throws IOException {
		int len = stream.readInt();
		String[] data = new String[len];
		for (int i = 0; i < len; i++) {
			boolean notNull = stream.readBoolean();
			if (notNull) {
				data[i] = stream.readUTF();
			}
		}
		return data;
	}

	/**
	 * write a Color to the given stream
	 *
	 * @param stream
	 * 		the stream to write to
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static void writeColor(SerializableDataOutputStream stream, Color data)
			throws IOException {
		float[] comps = data.getRGBComponents(null);
		stream.writeFloat(comps[0]); // r
		stream.writeFloat(comps[1]); // g
		stream.writeFloat(comps[2]); // b
		stream.writeFloat(comps[3]); // a
	}

	/**
	 * read a Color from the given stream
	 *
	 * @param stream
	 * 		the stream to read from
	 * @return the array that was read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static Color readColor(SerializableDataInputStream stream) throws IOException {
		float r = stream.readFloat();
		float g = stream.readFloat();
		float b = stream.readFloat();
		float a = stream.readFloat();
		return new Color(r, g, b, a);
	}

	/**
	 * write a Color array to the given stream
	 *
	 * @param stream
	 * 		the stream to write to
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static void writeColorArray(SerializableDataOutputStream stream, Color[] data)
			throws IOException {
		int len = (data == null ? 0 : data.length);
		stream.writeInt(len);
		for (int i = 0; i < len; i++) {
			writeColor(stream, data[i]);
		}
	}

	/**
	 * read a FastCopyable array from the given stream
	 *
	 * @param <T>
	 * 		the FastCopyable class that the array will consist of
	 * @param stream
	 * 		the stream to read from
	 * @param fastCopyableClass
	 * 		The class that of objects that is contained in the array, the class must implement
	 * 		FastCopyable and must have a default constructor
	 * @return the array that was read
	 * @throws Exception
	 * 		thrown if there are any problems during the operation
	 */
	@SuppressWarnings("unchecked")
	@Deprecated
	public static <T extends FastCopyable<T>> T[] readFastCopyableArray(
			SerializableDataInputStream stream, Class<T> fastCopyableClass)
			throws Exception {
		Constructor<?>[] constructors = fastCopyableClass
				.getDeclaredConstructors();
		Constructor<?> constructor = null;
		for (int i = 0; i < constructors.length; i++) {
			if (constructors[i].getGenericParameterTypes().length == 0) {
				constructor = constructors[i];
				break;
			}
		}
		if (constructor == null) {
			throw new RuntimeException(
					"The class must have a default constructor");
		}
		int len = stream.readInt();
		T[] data = (T[]) Array.newInstance(fastCopyableClass, len);
		for (int i = 0; i < len; i++) {
			// some of the array elements could be null, so the first byte that indicates whether it is
			// null or not
			byte isNull = stream.readByte();
			if (isNull != 0) {
				data[i] = (T) constructor.newInstance();
				data[i].copyFrom(stream);
			} else {
				data[i] = null;
			}

		}
		return data;
	}

	/**
	 * read a Color array from the given stream
	 *
	 * @param stream
	 * 		the stream to read from
	 * @return the array that was read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static Color[] readColorArray(SerializableDataInputStream stream)
			throws IOException {
		int len = stream.readInt();
		Color[] data = new Color[len];
		for (int i = 0; i < len; i++) {
			data[i] = readColor(stream);
		}
		return data;
	}

	/**
	 * Writes a list to the stream serializing the objects with the supplied method
	 *
	 * @param list
	 * 		the list to be serialized
	 * @param stream
	 * 		the stream to write to
	 * @param serializer
	 * 		the method used to write the object
	 * @param <T>
	 * 		the type of object being written
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static <T> void writeList(List<T> list, SerializableDataOutputStream stream,
			Serializer<T> serializer) throws IOException {
		if (list == null) {
			stream.writeInt(-1);
			return;
		}
		stream.writeInt(list.size());
		for (T t : list) {
			serializer.serialize(t, stream);
		}
	}

	/**
	 * Reads a list from the stream deserializing the objects with the supplied method
	 *
	 * @param stream
	 * 		the stream to read from
	 * @param listSupplier
	 * 		a method that supplies the list to add to
	 * @param deserializer
	 * 		a method used to deserialize the objects
	 * @param <T>
	 * 		the type of object contained in the list
	 * @return a list that was read from the stream, can be null if that was written
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static <T> List<T> readList(SerializableDataInputStream stream, Supplier<List<T>> listSupplier,
			Deserializer<T> deserializer) throws IOException {
		int listSize = stream.readInt();
		if (listSize < 0) {
			return null;
		}
		List<T> list = listSupplier.get();
		for (int i = 0; i < listSize; i++) {
			list.add(deserializer.deserialize(stream));
		}
		return list;
	}

	/**
	 * Convert the given long to bytes, big endian.
	 *
	 * @param n
	 * 		the long to convert
	 * @return a big-endian representation of n as an array of Long.BYTES bytes
	 */
	public static byte[] toBytes(long n) {
		byte[] bytes = new byte[Long.BYTES];
		toBytes(n, bytes, 0);
		return bytes;
	}

	/**
	 * Convert the given long to bytes, big endian, and put them into the array, starting at index start
	 *
	 * @param bytes
	 * 		the array to hold the Long.BYTES bytes of result
	 * @param n
	 * 		the long to convert to bytes
	 * @param start
	 * 		the bytes are written to Long.BYTES elements of the array, starting with this index
	 */
	public static void toBytes(long n, byte[] bytes, int start) {
		for (int i = start + Long.BYTES - 1; i >= start; i--) {
			bytes[i] = (byte) n;
			n >>>= 8;
		}
	}

	/**
	 * convert the given byte array to a long
	 *
	 * @param b
	 * 		the byte array to convert (at least 8 bytes)
	 * @return the long that was represented by the array
	 */
	public static long toLong(byte[] b) {
		return toLong(b, 0);
	}

	/**
	 * convert part of the given byte array to a long, starting with index start
	 *
	 * @param b
	 * 		the byte array to convert
	 * @param start
	 * 		the index of the first byte (most significant byte) of the 8 bytes to convert
	 * @return the long
	 */
	public static long toLong(byte[] b, int start) {
		long result = 0;
		for (int i = start; i < start + Long.BYTES; i++) {
			result <<= 8;
			result |= b[i] & 0xFF;
		}
		return result;
	}

	/**
	 * Concatenate a long followed by multiple byte arrays (some of which could be null).
	 *
	 * @param n
	 * 		the long to concatenate
	 * @param arrays
	 * 		the byte arrays
	 * @return the resulting array
	 */
	static byte[] concat(long n, byte[]... arrays) {
		return concat(toBytes(n), concat(arrays));
	}

	/**
	 * Concatenate a single byte followed by multiple byte arrays (some of which could be null).
	 *
	 * @param b
	 * 		the byte to concatenate first
	 * @param arrays
	 * 		the byte arrays
	 * @return the resulting array
	 */
	static byte[] concat(byte b, byte[]... arrays) {
		return concat(new byte[] { b }, concat(arrays));
	}

	/**
	 * Concatenate a single byte followed by a long followed by multiple byte arrays (some of which could be
	 * null).
	 *
	 * @param b
	 * 		the byte to concatenate first
	 * @param n
	 * 		the long to concatenate second
	 * @param arrays
	 * 		the byte arrays
	 * @return the resulting array of bytes
	 */
	public static byte[] concat(byte b, long n, byte[]... arrays) {
		return concat(b, concat(n, arrays));
	}

	/**
	 * Concatenate multiple byte arrays (some of which could be null).
	 *
	 * @param arrays
	 * 		the byte arrays
	 * @return the resulting array
	 */
	static byte[] concat(byte[]... arrays) {
		int len = 0;
		int pos = 0;
		for (int i = 0; i < arrays.length; i++) {
			len += arrays[i] == null ? 0 : arrays[i].length;
		}
		byte[] result = new byte[len];
		for (int i = 0; i < arrays.length; i++) {
			if (arrays[i] != null && arrays[i].length > 0) {
				System.arraycopy(arrays[i], 0, result, pos, arrays[i].length);
				pos += arrays[i].length;
			}
		}
		return result;
	}

	/**
	 * Convert an int to a byte array, little endian.
	 *
	 * @param i
	 * 		the int to convert
	 * @return the byte array
	 */
	static byte[] intToBytes(int i) {
		return new byte[] { (byte) i, (byte) (i >> 8), (byte) (i >> 16),
				(byte) (i) };
	}

	/**
	 * Return a string with info about all threads currently deadlocked. Each thread has a name of XYZ where
	 * X is the thread ID of the thread that created this thread, Y is a 2-letter code for its purpose, and
	 * Z is the Platform ID (0 for Alice, 1 for Bob, etc). See newThreadFromPool for the list of 2-letter codes.
	 * <p>
	 * If there are no deadlocked threads, then this method does nothing, and returns in slightly less than
	 * one millisecond, on average.
	 *
	 * @return a string describing all deadlocked threads, or null if there are none
	 */
	static String deadlocks() {
		ThreadMXBean threadMB = ManagementFactory.getThreadMXBean();
		long threadIds[] = threadMB.findDeadlockedThreads();
		if (threadIds == null) { // if it's null, then there are no deadlocked threads
			return null;
		}
		String err = "\nDEADLOCKED THREADS:\n";
		for (long tid : threadIds) {
			ThreadInfo t = threadMB.getThreadInfo(tid);
			err += "    thread " + t.getThreadName() + " blocked waiting on "
					+ threadMB.getThreadInfo(t.getLockOwnerId()).getThreadName()
					+ " to get the lock on "
					+ LoggingReentrantLock.getName(t.getLockInfo()) + " "
					+ t.getLockInfo().getIdentityHashCode() + "\n";
			for (StackTraceElement s : t.getStackTrace()) {
				err += "        " + s + "\n";
			}
		}
		return err;
	}


	/**
	 * Insert line breaks into the given string so that each line is at most len characters long (not
	 * including trailing whitespace and the line break iteslf). Line breaks are only inserted after a
	 * whitespace character and before a non-whitespace character, resulting in some lines possibly being
	 * shorter. If a line has no whitespace, then it will insert between two non-whitespace characters to
	 * make it exactly len characters long.
	 *
	 * @param len
	 * 		the desired length
	 * @param str
	 * 		the input string, where a newline is always just \n (never \n\r or \r or \r\n)
	 */
	static String wrap(int len, String str) {
		StringBuilder ans = new StringBuilder();
		String[] lines = str.split("\n"); // break into lines
		for (String line : lines) { // we'll add \n to end of every line, then strip off the last
			if (line.length() == 0) {
				ans.append('\n');
			}
			char[] c = line.toCharArray();
			int i = 0;
			while (i < c.length) {  // repeatedly add a string starting at i, followed by a \n
				int j = i + len;
				if (j >= c.length) { // grab the rest of the characters
					j = c.length;
				} else if (Character.isWhitespace(c[j])) { // grab more than len characters
					while (j < c.length && Character.isWhitespace(c[j])) {
						j++;
					}
				} else {// grab len or fewer characters
					while (j >= i && !Character.isWhitespace(c[j])) {
						j--;
					}
					if (j < i) {// there is no whitespace before len
						j = i + len;
					} else {// the last whitespace before len is at j
						j++;
					}
				}
				ans.append(c, i, j - i);// append c[i...j-1]
				ans.append('\n');
				i = j; // continue, starting at j
			}
		}
		return ans.substring(0, ans.length() - 1);// remove the last '\n' that was added
	}


	/**
	 * Is the part more than 2/3 of the whole?
	 *
	 * @param part
	 * 		a long value, the fraction of the whole being compared
	 * @param whole
	 * 		a long value, the whole being considered (such as the sum of the entire stake)
	 * @return true if part is more than two thirds of the whole
	 */
	public static boolean isSupermajority(long part, long whole) {

		return part > whole / 3 * 2 + (whole % 3) * 2 / 3;
	}

	/**
	 * Is the part 1/3 or more of the whole?
	 *
	 * @param part
	 * 		a long value, the fraction of the whole being compared
	 * @param whole
	 * 		a long value, the whole being considered (such as the sum of the entire stake)
	 * @return true if part is greater than or equal to one third of the whole
	 */
	public static boolean isStrongMinority(long part, long whole) {


		return part >= (whole / 3) + ((whole % 3 == 0) ? 0 : 1);
	}

	/**
	 * if it is or caused by SocketException,
	 * we should log it with SOCKET_EXCEPTIONS marker
	 *
	 * @param ex
	 * @return return true if it is a SocketException or is caused by SocketException;
	 * 		return false otherwise
	 */
	static boolean isOrCausedBySocketException(final Throwable ex) {
		if (ex == null) {
			return false;
		}

		return ex instanceof SocketException ||
				isOrCausedBySocketException(ex.getCause());
	}
}

