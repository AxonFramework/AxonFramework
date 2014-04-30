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

import org.apache.commons.io.input.CountingInputStream;
import org.axonframework.common.io.IOUtils;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.Serializer;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Writes snapshot events to the given {@link #snapshotEventFile}.
 *
 * @author Frank Versnel
 * @since 2.0
 */
public class FileSystemSnapshotEventWriter {

    private final InputStream eventFile;
    private final OutputStream snapshotEventFile;
    private final Serializer eventSerializer;

    /**
     * Creates a snapshot event writer that writes any given <code>snapshotEvent</code> to the given
     * <code>snapshotEventFile</code>.
     *
     * @param eventFile         used to determine the number of bytes to skip upon reading a snapshot
     *                          when using {@link FileSystemSnapshotEventReader#readSnapshotEvent(String, Object)}
     * @param snapshotEventFile the snapshot file to write to
     * @param eventSerializer   the serializer used to serialize snapshot events
     */
    public FileSystemSnapshotEventWriter(InputStream eventFile, OutputStream snapshotEventFile,
                                         Serializer eventSerializer) {
        this.eventFile = eventFile;
        this.snapshotEventFile = snapshotEventFile;
        this.eventSerializer = eventSerializer;
    }

    /**
     * Writes the given snapshotEvent to the {@link #snapshotEventFile}.
     * Prepends a long value to the event in the file indicating the bytes to skip when reading the {@link #eventFile}.
     *
     * @param snapshotEvent The snapshot to write to the {@link #snapshotEventFile}
     */
    public void writeSnapshotEvent(DomainEventMessage snapshotEvent) {
        try {
            long offset = calculateOffset(snapshotEvent);
            DataOutputStream dataOutputStream = new DataOutputStream(snapshotEventFile);

            dataOutputStream.writeLong(offset);
            FileSystemEventMessageWriter eventMessageWriter =
                    new FileSystemEventMessageWriter(dataOutputStream, eventSerializer);
            eventMessageWriter.writeEventMessage(snapshotEvent);
        } catch (IOException e) {
            throw new EventStoreException("Error writing a snapshot event due to an IO exception", e);
        } finally {
            IOUtils.closeQuietly(snapshotEventFile);
        }
    }

    /**
     * Calculate the bytes to skip when reading the event file.
     *
     * @param snapshotEvent the snapshot event
     * @return the bytes to skip when reading the event file
     *
     * @throws IOException when the {@link #eventFile} was closed unexpectedly
     */
    private long calculateOffset(DomainEventMessage snapshotEvent) throws IOException {
        CountingInputStream countingInputStream = null;
        try {
            countingInputStream = new CountingInputStream(new BufferedInputStream(eventFile));
            FileSystemEventMessageReader eventMessageReader =
                    new FileSystemEventMessageReader(new DataInputStream(countingInputStream));

            long lastReadSequenceNumber = -1;
            while (lastReadSequenceNumber < snapshotEvent.getSequenceNumber()) {
                SerializedDomainEventData entry = eventMessageReader.readEventMessage();
                lastReadSequenceNumber = entry.getSequenceNumber();
            }

            return countingInputStream.getByteCount();
        } finally {
            IOUtils.closeQuietly(countingInputStream);
        }
    }
}
