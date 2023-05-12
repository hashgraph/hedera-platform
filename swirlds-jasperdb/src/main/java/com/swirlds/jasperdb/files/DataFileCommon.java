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
package com.swirlds.jasperdb.files;

import static com.swirlds.common.utility.Units.GIBIBYTES_TO_BYTES;
import static com.swirlds.common.utility.Units.KIBIBYTES_TO_BYTES;
import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.JASPER_DB;

import com.swirlds.jasperdb.KeyRange;
import com.swirlds.jasperdb.collections.IndexedObject;
import com.swirlds.jasperdb.collections.LongList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common static content for data files. As much as possible is package protected but some is used
 * outside.
 */
@SuppressWarnings("rawtypes")
public final class DataFileCommon {
    private static final Logger logger = LogManager.getLogger(DataFileCommon.class);

    /** The inverse of the minimum decimal value to be reflected in rounding (that is, 0.01). */
    private static final int ROUNDING_SCALE_FACTOR = 100;
    /** Size of right-justified field to use when printing indexes. */
    private static final int PRINTED_INDEX_FIELD_WIDTH = 10;
    /** Data size constant used when the data size is variable */
    public static final int VARIABLE_DATA_SIZE = -1;
    /**
     * Nominal value to indicate a non-existent data location. This was carefully crafted to be 0 so
     * that a new long array of data location pointers will be initialized to be all non-existent.
     */
    public static final long NON_EXISTENT_DATA_LOCATION = 0;

    /**
     * The data item byte offset is packed into lower 40 bits and file index upper 24 bits. This
     * allows for 16 million files 1 trillion bytes of data. So at one file per minute we have 30
     * years of 1Tb files.
     */
    /* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3927 */
    private static final int DATA_ITEM_OFFSET_BITS = 40;
    /** The maximum size a data file can be, 1Tb with DATA_ITEM_OFFSET_BITS = 40 */
    private static final long MAX_ADDRESSABLE_DATA_FILE_SIZE_BYTES = 1L << DATA_ITEM_OFFSET_BITS;
    /** Bit mask to remove file index from data location long */
    private static final long ITEM_OFFSET_MASK = MAX_ADDRESSABLE_DATA_FILE_SIZE_BYTES - 1;

    /** The current file format version, ready for if the file format needs to change */
    public static final int FILE_FORMAT_VERSION = 1;
    /** Date formatter for dates used in data file names */
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS").withZone(ZoneId.of("Z"));
    /** Extension to use for Jasper DB data files :-) */
    private static final String FILE_EXTENSION = ".jdb";
    /**
     * System page size used in calculations, could be read from system but for linux we are pretty
     * safe assuming 4k
     */
    public static final int PAGE_SIZE = 4096;
    /** Size of metadata footer written at end of file */
    public static final int FOOTER_SIZE = PAGE_SIZE;
    /** Comparator for comparing DataFileReaders by file creation time */
    private static final Comparator<DataFileReader> DATA_FILE_READER_CREATION_TIME_COMPARATOR =
            Comparator.comparing(o -> o.getMetadata().getCreationDate());
    /** Comparator for comparing DataFileReaders by file creation time reversed */
    private static final Comparator<DataFileReader>
            DATA_FILE_READER_CREATION_TIME_COMPARATOR_REVERSED =
                    DATA_FILE_READER_CREATION_TIME_COMPARATOR.reversed();

    private DataFileCommon() {
        throw new IllegalStateException("Utility class; should not be instantiated.");
    }

    /**
     * Create a filter to only return all new files that are smaller than given size
     *
     * @param sizeMB max file size to accept in MB
     * @param maxNumberOfFilesInMerge The maximum number of files to process in a single merge
     * @return filter to filter list of files
     */
    public static UnaryOperator<List<DataFileReader>> newestFilesSmallerThan(
            final int sizeMB, final int maxNumberOfFilesInMerge) {
        final long sizeBytes = sizeMB * (long) MEBIBYTES_TO_BYTES;

        return dataFileReaders -> {
            final List<DataFileReader> filesNewestFirst =
                    dataFileReaders.stream()
                            .sorted(DATA_FILE_READER_CREATION_TIME_COMPARATOR_REVERSED)
                            .toList();
            final ArrayList<DataFileReader> smallEnoughFiles =
                    new ArrayList<>(filesNewestFirst.size());
            for (final DataFileReader file : filesNewestFirst) {
                long size = file.getSize();
                if (size < sizeBytes) {
                    smallEnoughFiles.add(file);
                } else {
                    break;
                }
            }

            final var numFiles = smallEnoughFiles.size();
            return numFiles > maxNumberOfFilesInMerge
                    ? smallEnoughFiles.subList(numFiles - maxNumberOfFilesInMerge, numFiles)
                    : smallEnoughFiles;
        };
    }

    /**
     * Get path for file given prefix, index and parent directory. This standardizes out file naming
     * convention.
     *
     * @param filePrefix the prefix for file name
     * @param dataFileDir the files parent directory
     * @param index the file index
     * @param creationInstant the date and time the file was created
     * @return path to file
     */
    static Path createDataFilePath(
            final String filePrefix,
            final Path dataFileDir,
            final int index,
            final Instant creationInstant) {
        return dataFileDir.resolve(
                filePrefix
                        + "_"
                        + DATE_FORMAT.format(creationInstant)
                        + "_"
                        + StringUtils.leftPad(
                                Integer.toString(index), PRINTED_INDEX_FIELD_WIDTH, '_')
                        + FILE_EXTENSION);
    }

    /** Get the path for a lock file for a given data file path */
    static Path getLockFilePath(final Path dataFilePath) {
        return dataFilePath.resolveSibling(dataFilePath.getFileName().toString() + ".lock");
    }

    /**
     * Get the packed data location from file index and byte offset.
     *
     * @param fileIndex the index for the file
     * @param byteOffset the offset for the data within the file in bytes
     * @return packed data location
     */
    public static long dataLocation(final int fileIndex, final long byteOffset) {
        // we add 1 to file index so that 0 works for NON_EXISTENT_DATA_LOCATION
        final long indexShifted = (long) (fileIndex + 1) << DATA_ITEM_OFFSET_BITS;
        final long byteOffsetMasked = byteOffset & ITEM_OFFSET_MASK;
        return indexShifted | byteOffsetMasked;
    }

    /**
     * Get a friendly string with the disk data location split into its file and offset parts. Very
     * useful for debugging and logging.
     *
     * @param dataLocation Packed disk location containing file and offset.
     * @return String with split file and offset
     */
    public static String dataLocationToString(final long dataLocation) {
        return "{"
                + fileIndexFromDataLocation(dataLocation)
                + ","
                + byteOffsetFromDataLocation(dataLocation)
                + "}";
    }

    /**
     * Extract the file index from packed data location, this is the upper 24 bits. So in the range
     * of 0 to 16 million.
     *
     * @param dataLocation packed data location
     * @return file index
     */
    public static int fileIndexFromDataLocation(final long dataLocation) {
        // we subtract 1 from file index so that 0 works for NON_EXISTENT_DATA_LOCATION
        return (int) (dataLocation >> DATA_ITEM_OFFSET_BITS) - 1;
    }

    /**
     * Extract the data byte offset from packed data location, this is the lower 40 bits so in the 0
     * to 1 trillion range.
     *
     * @param dataLocation packed data location
     * @return data offset in bytes
     */
    static long byteOffsetFromDataLocation(final long dataLocation) {
        return dataLocation & ITEM_OFFSET_MASK;
    }

    /**
     * Check if a file at path, is a data file based on name. Also checks if there is an existing
     * write lock file.
     *
     * @param filePrefix the prefix for the set of data files
     * @param path the path to the data file
     * @return true if the name starts with prefix and has right extension
     */
    static boolean isFullyWrittenDataFile(final String filePrefix, final Path path) {
        final String fileName = path.getFileName().toString();
        final boolean validFile =
                fileName.startsWith(filePrefix) && fileName.endsWith(FILE_EXTENSION);
        if (!validFile) {
            return false;
        }
        return !Files.exists(getLockFilePath(path));
    }

    /** print debug info showing if all links in index are still valid */
    static <D> void printDataLinkValidation(
            final String storeName,
            final LongList index,
            final Set<Integer> newFileIndexes,
            final List<DataFileReader<D>> fileList,
            final KeyRange validKeyRange) {
        final SortedSet<Integer> validFileIds = new TreeSet<>();
        int newestFileIndex = 0;
        for (final DataFileReader<D> file : fileList) {
            final int fileIndex = file.getMetadata().getIndex();
            validFileIds.add(fileIndex);
            if (fileIndex > newestFileIndex) {
                newestFileIndex = fileIndex;
            }
        }
        final int finalNewestFileIndex = newestFileIndex;

        final ConcurrentMap<Integer, Long> missingFileCounts =
                LongStream.range(validKeyRange.getMinValidKey(), validKeyRange.getMaxValidKey() + 1)
                        .parallel()
                        .map(key -> index.get(key, -1))
                        .filter(
                                location -> {
                                    final int fileIndex =
                                            DataFileCommon.fileIndexFromDataLocation(location);
                                    return !(validFileIds.contains(fileIndex)
                                            || fileIndex > finalNewestFileIndex
                                            || newFileIndexes.contains(fileIndex));
                                })
                        .boxed()
                        .collect(
                                Collectors.groupingByConcurrent(
                                        DataFileCommon::fileIndexFromDataLocation,
                                        Collectors.counting()));

        if (!missingFileCounts.isEmpty()) {
            logger.trace(
                    JASPER_DB.getMarker(),
                    "{}:printDataLinkValidation index size={} numOfFiles={}, fileIndexes={},"
                            + " newFileIndexes={}",
                    () -> storeName,
                    index::size,
                    fileList::size,
                    () ->
                            Arrays.toString(
                                    fileList.stream().mapToInt(IndexedObject::getIndex).toArray()),
                    () -> newFileIndexes);
            missingFileCounts.forEach(
                    (id, count) ->
                            logger.trace(
                                    JASPER_DB.getMarker(),
                                    "{}:       missing file {} has {} references",
                                    storeName,
                                    id,
                                    count));
            logger.error(
                    EXCEPTION.getMarker(),
                    "{} has references to files {} that don't exists in the index. "
                            + "Latest new files = {}",
                    storeName,
                    missingFileCounts.keySet().toString(),
                    newFileIndexes);
        }
    }

    /**
     * Get total size fo a collection of files.
     *
     * @param filePaths collection of paths to files
     * @return total number of bytes take for all the files in filePaths
     * @throws IOException If there was a problem getting file sizes
     */
    public static long getSizeOfFilesByPath(final Iterable<Path> filePaths) throws IOException {
        long totalSize = 0;
        for (final Path path : filePaths) {
            totalSize += Files.size(path);
        }
        return totalSize;
    }

    /**
     * Get total size fo a collection of files.
     *
     * @param filePaths collection of paths to files
     * @return total number of bytes take for all the files in filePaths
     */
    public static <D> long getSizeOfFiles(final Iterable<DataFileReader<D>> filePaths) {
        long totalSize = 0;
        for (final DataFileReader<D> dataFileReader : filePaths) {
            totalSize += dataFileReader.getSize();
        }
        return totalSize;
    }

    /**
     * Return a nice string for size of bytes.
     *
     * @param numOfBytes number of bytes
     * @return formatted string
     */
    public static String formatSizeBytes(final long numOfBytes) {
        if (numOfBytes <= KIBIBYTES_TO_BYTES) {
            return numOfBytes + " bytes";
        } else {
            return formatLargeDenomBytes(numOfBytes);
        }
    }

    private static String formatLargeDenomBytes(final long numOfBytes) {
        if (numOfBytes > GIBIBYTES_TO_BYTES) {
            final double numOfGb = numOfBytes / (double) GIBIBYTES_TO_BYTES;
            return roundTwoDecimals(numOfGb) + " GB";
        } else if (numOfBytes > MEBIBYTES_TO_BYTES) {
            final double numOfMb = numOfBytes / (double) MEBIBYTES_TO_BYTES;
            return roundTwoDecimals(numOfMb) + " MB";
        } else {
            final double numOfKb = numOfBytes / (double) KIBIBYTES_TO_BYTES;
            return roundTwoDecimals(numOfKb) + " KB";
        }
    }

    /**
     * Round a decimal to two decimal places
     *
     * @param d number to round
     * @return rounded number
     */
    public static double roundTwoDecimals(final double d) {
        return (double) Math.round(d * ROUNDING_SCALE_FACTOR) / ROUNDING_SCALE_FACTOR;
    }

    public static <D> void logMergeStats(
            final String storeName,
            final double tookSeconds,
            final long filesToMergeSize,
            final long mergedFilesCreatedSize,
            final DataFileCollection<D> fileCollection,
            final Collection<DataFileReader<D>> filesToMerge,
            final Collection<DataFileReader<D>> allMergeableFiles) {
        logger.info(
                JASPER_DB.getMarker(),
                """
						[{}] Merged {} files into {} files in {} seconds. Read at {} Written at {}
						        filesToMerge = {} allMergeableFiles = {}
						        allFilesAfter = {}""",
                storeName,
                formatSizeBytes(filesToMergeSize),
                formatSizeBytes(mergedFilesCreatedSize),
                tookSeconds,
                formatSizeBytes((long) (filesToMergeSize / tookSeconds)) + "/sec",
                formatSizeBytes((long) (mergedFilesCreatedSize / tookSeconds)) + "/sec",
                Arrays.toString(
                        filesToMerge.stream()
                                .map(reader -> reader.getMetadata().getIndex())
                                .toArray()),
                Arrays.toString(
                        allMergeableFiles.stream()
                                .map(reader -> reader.getMetadata().getIndex())
                                .toArray()),
                Arrays.toString(
                        fileCollection.getAllFullyWrittenFiles().stream()
                                .map(reader -> reader.getMetadata().getIndex())
                                .toArray()));
    }

    /**
     * Delete a directory and all its contents if it exists. Does nothing if directory does not
     * exist.
     *
     * @param dir The directory to delete
     */
    public static void deleteDirectoryAndContents(final Path dir) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try (Stream<Path> filesStream = Files.walk(dir)) {
                //noinspection ResultOfMethodCallIgnored
                filesStream
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                Files.deleteIfExists(dir);
                logger.info(
                        JASPER_DB.getMarker(),
                        "Deleted data directory [{}]",
                        dir.toFile().getAbsolutePath());
            } catch (Exception e) {
                logger.warn(
                        EXCEPTION.getMarker(),
                        "Failed to delete test directory [{}]",
                        dir.toFile().getAbsolutePath(),
                        e);
            }
        }
    }
}
