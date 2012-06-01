package org.axonframework.eventstore.fs;

import java.io.DataInput;
import java.io.IOException;

/**
 * Reads events from the file system returns an event in a raw form as {@link FileSystemEventEntry}.
 *
 * @author Frank Versnel
 * @since 2.0
 */
public class FileSystemEventMessageReader {

    private final DataInput in;

    /**
     * Creates a new EventMessageReader that reads the data from the given <code>input</code> and deserializes payload
     * and meta data using the given <code>serializer</code>.
     *
     * @param input      The input providing access to the written data
     */
    public FileSystemEventMessageReader(DataInput input) {
        this.in = input;
    }

    /**
     * Reads the next event from the input.
     *
     * @return the event read from the input
     *
     * @throws IOException          when an error occurs reading from the underlying input
     * @throws java.io.EOFException when the end of the stream was reached before the message was entirely read
     */
    public FileSystemEventEntry readEventMessage() throws IOException {
        String identifier = in.readUTF();
        String timestamp = in.readUTF();
        String aggregateIdentifier = in.readUTF();
        long sequenceNumber = in.readLong();

        String payloadType = in.readUTF();
        String payloadRevision = in.readUTF();
        byte[] payload = new byte[in.readInt()];
        in.readFully(payload);

        int metaDataSize = in.readInt();
        byte[] metaData = new byte[metaDataSize];
        in.readFully(metaData);

        return new FileSystemEventEntry(identifier,
                                        aggregateIdentifier,
                                        sequenceNumber,
                                        timestamp,
                                        payloadType,
                                        payloadRevision,
                                        metaData,
                                        payload);
    }
}
