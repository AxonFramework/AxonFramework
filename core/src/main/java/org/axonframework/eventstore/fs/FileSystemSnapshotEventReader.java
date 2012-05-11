package org.axonframework.eventstore.fs;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.io.EventMessageReader;
import org.axonframework.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
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

    private InputStream eventFile;
    private InputStream snapshotEventFile;
    private Serializer eventSerializer;

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
     * @param type       the aggregate's type
     * @param identifier the aggregate's identifier
     * @return The latest snapshot of the given aggregate identifier
     *
     * @throws IOException when reading the <code>snapshotEventFile</code> or reading the <code>eventFile</code> failed
     */
    public DomainEventMessage readSnapshotEvent(String type, Object identifier) throws IOException {
        DomainEventMessage snapshotEvent = null;

        FileSystemSnapshotEventEntry fileSystemSnapshotEvent = readLastSnapshotEntry();
        if(fileSystemSnapshotEvent != null) {
            long actuallySkipped = eventFile.skip(fileSystemSnapshotEvent.getBytesToSkipInEventFile());
            if (actuallySkipped != fileSystemSnapshotEvent.getBytesToSkipInEventFile()) {
                logger.warn(
                        "The skip operation did not actually skip the expected amount of bytes. "
                                + "The event log of aggregate of type {} and identifier {} might be corrupt.",
                        type, identifier);
            }
            snapshotEvent = fileSystemSnapshotEvent.getEventMessage();
        }

        return snapshotEvent;
    }

    private FileSystemSnapshotEventEntry readLastSnapshotEntry() throws IOException {
        DataInputStream snapshotEventFileDataInputStream = new DataInputStream(snapshotEventFile);
        EventMessageReader snapshotEventReader =
                new EventMessageReader(snapshotEventFileDataInputStream, eventSerializer);

        FileSystemSnapshotEventEntry lastSnapshotEvent = null;
        while (snapshotEventFileDataInputStream.available() > 0) {
            long bytesToSkip = snapshotEventFileDataInputStream.readLong();
            EventMessage snapshotEvent = snapshotEventReader.readEventMessage();
            lastSnapshotEvent = new FileSystemSnapshotEventEntry((DomainEventMessage) snapshotEvent, bytesToSkip);
        }

        return lastSnapshotEvent;
    }

    private static class FileSystemSnapshotEventEntry {

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
