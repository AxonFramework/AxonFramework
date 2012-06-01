package org.axonframework.eventstore.fs;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Writes a domain event message to the given {@link DataOutput}.
 *
 * @author Frank Versnel
 * @since 2.0
 */
public class FileSystemEventMessageWriter {

    private final Serializer serializer;
    private final DataOutput out;

    /**
     * Creates a new EventMessageWriter writing data to the specified underlying <code>output</code>.
     *
     * @param output     the underlying output
     * @param serializer The serializer to deserialize payload and metadata with
     */
    public FileSystemEventMessageWriter(DataOutput output, Serializer serializer) {
        this.out = output;
        this.serializer = serializer;
    }

    /**
     * Writes the given <code>eventMessage</code> to the underling output.
     *
     * @param eventMessage the EventMessage to write to the underlying output
     * @throws java.io.IOException when any exception occurs writing to the underlying stream
     */
    public void writeEventMessage(DomainEventMessage eventMessage) throws IOException {
        out.writeUTF(eventMessage.getIdentifier());
        out.writeUTF(eventMessage.getTimestamp().toString());
        out.writeUTF(eventMessage.getAggregateIdentifier().toString());
        out.writeLong(eventMessage.getSequenceNumber());

        SerializedObject<byte[]> serializedPayload = serializer.serialize(eventMessage.getPayload(), byte[].class);
        SerializedObject<byte[]> serializedMetaData = serializer.serialize(eventMessage.getMetaData(), byte[].class);

        out.writeUTF(serializedPayload.getType().getName());
        String revision = serializedPayload.getType().getRevision();
        out.writeUTF(revision == null ? "" : revision);
        out.writeInt(serializedPayload.getData().length);
        out.write(serializedPayload.getData());
        out.writeInt(serializedMetaData.getData().length);
        out.write(serializedMetaData.getData());
    }
}
