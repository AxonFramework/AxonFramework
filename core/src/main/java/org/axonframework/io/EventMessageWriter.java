package org.axonframework.io;


import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Writer that writes Event Messages onto a an OutputStream. The format of the message makes them suitable to be read
 * back in using a {@link EventMessageReader}. This writer distinguishes between DomainEventMessage and plain
 * EventMessage when writing. The reader will reconstruct an aggregate implementation for the same message type (i.e.
 * DomainEventMessage or EventMessage).
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class EventMessageWriter {

    protected final Serializer serializer;
    protected final DataOutput out;

    /**
     * Creates a new EventMessageWriter writing data to the specified underlying <code>output</code>.
     *
     * @param output     the underlying output
     * @param serializer The serializer to deserialize payload and metadata with
     */
    public EventMessageWriter(DataOutput output, Serializer serializer) {
        this.out = output;
        this.serializer = serializer;
    }

    /**
     * Writes the given <code>eventMessage</code> to the underling output.
     *
     * @param eventMessage the EventMessage to write to the underlying output
     * @throws IOException when any exception occurs writing to the underlying stream
     */
    public void writeEventMessage(EventMessage eventMessage) throws IOException {
        out.writeByte(EventMessageType.forMessage(eventMessage).getTypeByte());
        out.writeUTF(eventMessage.getIdentifier());
        out.writeUTF(eventMessage.getTimestamp().toString());
        if (eventMessage instanceof DomainEventMessage) {
            DomainEventMessage domainEventMessage = (DomainEventMessage) eventMessage;
            out.writeUTF(domainEventMessage.getAggregateIdentifier().toString());
            out.writeLong(domainEventMessage.getSequenceNumber());
        }
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
