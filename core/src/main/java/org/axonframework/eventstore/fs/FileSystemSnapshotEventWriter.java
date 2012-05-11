package org.axonframework.eventstore.fs;

import org.apache.commons.io.input.CountingInputStream;
import org.axonframework.common.io.IOUtils;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.io.EventMessageReader;
import org.axonframework.io.EventMessageWriter;
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

    private InputStream eventFile;
    private OutputStream snapshotEventFile;
    private Serializer eventSerializer;

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
            EventMessageWriter eventMessageWriter = new EventMessageWriter(dataOutputStream, eventSerializer);
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
            EventMessageReader eventMessageReader =
                    new EventMessageReader(new DataInputStream(countingInputStream), eventSerializer);

            long lastReadSequenceNumber = -1;
            while (lastReadSequenceNumber < snapshotEvent.getSequenceNumber()) {
                DomainEventMessage entry = (DomainEventMessage) eventMessageReader.readEventMessage();
                lastReadSequenceNumber = entry.getSequenceNumber();
            }

            return countingInputStream.getByteCount();
        } finally {
            IOUtils.closeQuietly(countingInputStream);
        }
    }
}
