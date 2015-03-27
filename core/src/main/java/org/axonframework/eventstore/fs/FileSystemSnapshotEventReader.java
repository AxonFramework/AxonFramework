/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.fs;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventMessage;
import org.axonframework.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the latest snapshot event from a given {@link #snapshotEventFile} and
 * skips the correct number of bytes in the given {@link #eventFile}.
 *
 * @author Frank Versnel
 * @since 2.0
 */
public class FileSystemSnapshotEventReader {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemSnapshotEventReader.class);

    private final InputStream eventFile;
    private final InputStream snapshotEventFile;
    private final Serializer eventSerializer;

    /**
     * Creates a snapshot event reader that reads the latest snapshot from the <code>snapshotEventFile</code>.
     *
     * @param eventFile         used to skip the number of bytes specified by the latest snapshot
     * @param snapshotEventFile the file to read snapshots from
     * @param eventSerializer   the serializer that is used to deserialize events in snapshot file
     */
    public FileSystemSnapshotEventReader(InputStream eventFile, InputStream snapshotEventFile,
                                         Serializer eventSerializer) {
        this.eventFile = eventFile;
        this.snapshotEventFile = snapshotEventFile;
        this.eventSerializer = eventSerializer;
    }

    /**
     * Reads the latest snapshot of the given aggregate identifier.
     *
     * @param identifier the aggregate's identifier
     * @return The latest snapshot of the given aggregate identifier
     *
     * @throws IOException when reading the <code>snapshotEventFile</code> or reading the <code>eventFile</code> failed
     */
    public DomainEventMessage readSnapshotEvent(String identifier) throws IOException {
        DomainEventMessage snapshotEvent = null;

        FileSystemSnapshotEventEntry fileSystemSnapshotEvent = readLastSnapshotEntry();
        if (fileSystemSnapshotEvent != null) {
            long actuallySkipped = eventFile.skip(fileSystemSnapshotEvent.getBytesToSkipInEventFile());
            if (actuallySkipped != fileSystemSnapshotEvent.getBytesToSkipInEventFile()) {
                logger.warn(
                        "The skip operation did not actually skip the expected amount of bytes. "
                                + "The event log of aggregate with identifier {} might be corrupt.",
                        identifier);
            }
            snapshotEvent = fileSystemSnapshotEvent.getEventMessage();
        }

        return snapshotEvent;
    }

    private FileSystemSnapshotEventEntry readLastSnapshotEntry() throws IOException {
        FileSystemSnapshotEventEntry lastSnapshotEvent = null;
        DataInputStream snapshotEventFileDataInputStream = new DataInputStream(snapshotEventFile);
        FileSystemSnapshotEventEntry snapshotEvent;
        do {
            snapshotEvent = readSnapshotEventEntry(snapshotEventFileDataInputStream);

            if (snapshotEvent != null) {
                lastSnapshotEvent = snapshotEvent;
            }
        } while (snapshotEvent != null);

        return lastSnapshotEvent;
    }

    private FileSystemSnapshotEventEntry readSnapshotEventEntry(DataInputStream snapshotEventFileDataInputStream)
            throws IOException {
        FileSystemEventMessageReader snapshotEventReader =
                new FileSystemEventMessageReader(snapshotEventFileDataInputStream);
        try {
            long bytesToSkip = snapshotEventFileDataInputStream.readLong();
            SerializedDomainEventData snapshotEventData = snapshotEventReader.readEventMessage();
            SerializedDomainEventMessage<Object> snapshotEvent =
                    new SerializedDomainEventMessage<>(snapshotEventData, eventSerializer);
            return new FileSystemSnapshotEventEntry(snapshotEvent, bytesToSkip);
        } catch (EOFException e) {
            // No more events available
            return null;
        }
    }

    private static final class FileSystemSnapshotEventEntry {

        private final DomainEventMessage eventMessage;
        private final long bytesToSkipInEventFile;

        private FileSystemSnapshotEventEntry(DomainEventMessage eventMessage, long bytesToSkipInEventFile) {
            this.eventMessage = eventMessage;
            this.bytesToSkipInEventFile = bytesToSkipInEventFile;
        }

        public DomainEventMessage getEventMessage() {
            return eventMessage;
        }

        public long getBytesToSkipInEventFile() {
            return bytesToSkipInEventFile;
        }
    }
}
