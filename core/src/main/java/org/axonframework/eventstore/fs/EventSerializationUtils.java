/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.fs;

import org.axonframework.util.io.BinaryEntryInputStream;
import org.axonframework.util.io.BinaryEntryOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class that provides operations to read and write event entries for event logs. This class supports both
 * entries for domain events, as well as snapshot events.
 * <p/>
 * DomainEvent entries consist of two components, split by at least one whitespace character: <ul><li>A numeric value
 * indicating the size of the second component, in bytes</li><li>The serialized event object</li></ul>
 * <p/>
 * Snapshot events have an extra component:<ul><li>A numeric value indicating the size of the second component, in
 * bytes</li><li>The number of bytes that may be skipped from the event log when applying this snapshot
 * event</li><li>The serialized snapshot event object</li></ul>
 *
 * @author Allard Buijze
 * @since 0.5
 */
abstract class EventSerializationUtils {

    private static final Logger logger = LoggerFactory.getLogger(EventSerializationUtils.class);

    private static final int LATEST_ENTRY_VERSION = 0;

    private EventSerializationUtils() {
        // utility class
    }

    /**
     * Reads a DomainEvent from the input stream. The input stream should contain entries of domain events using three
     * components. The first is the size of the serialized event in bytes, the seconds is the sequence number of the
     * aggregate, and the third the actual bytes of the serialized event. All components must be separated by at least
     * on whitespace character (see {@link Character#isWhitespace(int)}.
     * <p/>
     * The pointer of the input stream is advanced to the end of the DomainEvent entry.
     *
     * @param inputStream The stream delivering the raw data.
     * @return An EventEntry representing the serialized event, or <code>null</code> if no next event exists
     *
     * @throws IOException when an error occurs reading from the input stream.
     */
    public static EventEntry readEventEntry(InputStream inputStream) throws IOException {
        BinaryEntryInputStream in = new BinaryEntryInputStream(inputStream);
        int version = (int) in.readNumber();
        if (version < 0) {
            return null;
        }

        int sequenceNumber = (int) in.readNumber();
        String timeStamp = in.readString();
        byte[] serializedEvent = in.readBytes();
        if (serializedEvent == null) {
            logger.warn("Failed to read the required amount of bytes from the underlying stream.");
            return null;
        }
        return new EventEntry(sequenceNumber, timeStamp, serializedEvent);
    }

    /**
     * Writes a DomainEvent entry to the output stream. An entry consists of two components. The first is the size of
     * the serialized event in bytes, the seconds is the actual bytes of the serialized event. Components are separated
     * by a whitespace character (see {@link Character#isWhitespace(int)}. After the entry, a newline character is
     * written.
     *
     * @param outputStream    The stream delivering the raw data.
     * @param sequenceNumber  The sequence number of the event to write
     * @param timeStamp       The ISO8601 formatted timestamp
     * @param serializedEvent The bytes of the serialized event
     * @throws IOException when an error occurs writing to the output stream.
     */
    public static void writeEventEntry(OutputStream outputStream, long sequenceNumber, String timeStamp,
                                       byte[] serializedEvent)
            throws IOException {
        BinaryEntryOutputStream out = new BinaryEntryOutputStream(outputStream);
        out.writeNumber(LATEST_ENTRY_VERSION);
        out.writeNumber(sequenceNumber);
        out.writeString(timeStamp);
        out.writeBytes(serializedEvent);
    }

    /**
     * Reads the last snapshot event entry from the input stream. The input stream should contain entries of domain
     * events using three components. The first is the size of the serialized event in bytes, the second is the offset
     * to use when reading events from the regular stream and the third is the actual bytes of the serialized event. All
     * components must be separated by at least one whitespace character (see {@link Character#isWhitespace(int)}.
     * <p/>
     * The offset is the number of bytes that may be skipped when reading from the event log, when using the snapshot
     * event from the entry.
     * <p/>
     * The pointer of the input stream is advanced to the end of the input stream.
     *
     * @param inputStream The stream delivering the raw data.
     * @return The bytes of the serialized event
     *
     * @throws IOException when an error occurs reading from the input stream.
     */
    public static SnapshotEventEntry readLastSnapshotEntry(InputStream inputStream) throws IOException {
        SnapshotEventEntry lastValidEntry = readNextSnapshotEntry(inputStream);
        if (lastValidEntry == null) {
            return null;
        }
        SnapshotEventEntry currentEntry = lastValidEntry;
        while (currentEntry != null) {
            currentEntry = readNextSnapshotEntry(inputStream);
            if (currentEntry != null) {
                lastValidEntry = currentEntry;
            }
        }
        return lastValidEntry;
    }

    /**
     * Writes a snapshot event entry to the output stream. The entry consists of three components. The first is the size
     * of the serialized event in bytes, the second is the offset to use when reading events from the regular stream and
     * the third is the actual bytes of the serialized event. All components are separated by a whitespace character
     * (see {@link Character#isWhitespace(int)}. After the entry, a newline character is written.
     * <p/>
     * The offset is the number of bytes that may be skipped when reading from the event log, when using the snapshot
     * event from the entry.
     * <p/>
     * The pointer of the input stream is advanced by the size of a single snapshot event entry.
     *
     * @param outputStream  The stream to write the raw data to.
     * @param snapshotEntry The snapshot entry containing a serialized snapshot event and the related offset
     * @throws IOException when an error occurs writing to the output stream.
     */
    public static void writeSnapshotEntry(OutputStream outputStream, SnapshotEventEntry snapshotEntry)
            throws IOException {
        BinaryEntryOutputStream out = new BinaryEntryOutputStream(outputStream);
        out.writeNumber(LATEST_ENTRY_VERSION);
        out.writeNumber(snapshotEntry.getSequenceNumber());
        out.writeString(snapshotEntry.getTimeStamp());
        out.writeNumber(snapshotEntry.getOffset());
        out.writeBytes(snapshotEntry.getBytes());
    }

    private static SnapshotEventEntry readNextSnapshotEntry(InputStream inputStream) throws IOException {
        BinaryEntryInputStream in = new BinaryEntryInputStream(inputStream);
        int version = (int) in.readNumber();
        long sequenceNumber = in.readNumber();
        String timeStamp = in.readString();
        long offset = in.readNumber();
        if (version < 0 || sequenceNumber < 0 || offset < 0) {
            return null;
        }
        byte[] serializedEvent = in.readBytes();
        return new SnapshotEventEntry(serializedEvent, sequenceNumber, timeStamp, offset);
    }
}