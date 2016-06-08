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

package org.axonframework.eventhandling.io;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.time.Instant;

/**
 * Reader that reads EventMessage instances written to the underlying input. Typically, these messages have been written
 * using a {@link EventMessageWriter}. This reader distinguishes between DomainEventMessage and regular EventMessage
 * implementations and will reconstruct an instance implementing that same interface when reading.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class EventMessageReader {

    private final Serializer serializer;
    private final DataInputStream in;

    /**
     * Creates a new EventMessageReader that reads the data from the given <code>input</code> and deserializes payload
     * and meta data using the given <code>serializer</code>.
     *
     * @param input      The input providing access to the written data
     * @param serializer The serializer to deserialize payload and meta data with
     */
    public EventMessageReader(DataInputStream input, Serializer serializer) {
        this.in = input;
        this.serializer = serializer;
    }

    /**
     * Reads an EventMessage from the underlying input. If the written event was a DomainEventMessage, an instance of
     * DomainEventMessage is returned.
     *
     * @param <T> The type of payload expected to be in the returned EventMessage. This is not checked at runtime!
     * @return an EventMessage representing the message originally written, or <code>null</code> if the stream has
     * reached the end
     * @throws IOException                                                    when an error occurs reading from the
     *                                                                        underlying input
     * @throws java.io.EOFException                                           when the end of the stream was reached
     *                                                                        before the message was entirely read
     * @throws org.axonframework.serialization.UnknownSerializedTypeException if the type of the serialized object
     *                                                                        cannot be resolved to a class
     */
    public <T> EventMessage<T> readEventMessage() throws IOException {
        final int firstByte = in.read();
        if (firstByte == -1) {
            // end of stream
            return null;
        }
        EventMessageType messageType = EventMessageType.fromTypeByte((byte) firstByte);
        String identifier = in.readUTF();
        String timestamp = in.readUTF();
        String type = null;
        String aggregateIdentifier = null;
        long sequenceNumber = 0;
        if (messageType == EventMessageType.DOMAIN_EVENT_MESSAGE) {
            type = in.readUTF();
            aggregateIdentifier = in.readUTF();
            sequenceNumber = in.readLong();
        }
        String payloadType = in.readUTF();
        String payloadRevision = in.readUTF();
        byte[] payload = new byte[in.readInt()];
        in.readFully(payload);
        int metaDataSize = in.readInt();
        byte[] metaData = new byte[metaDataSize];
        in.readFully(metaData);
        SimpleSerializedObject<byte[]> serializedPayload =
                new SimpleSerializedObject<>(payload, byte[].class, payloadType, payloadRevision);
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(metaData, byte[].class);

        if (messageType == EventMessageType.DOMAIN_EVENT_MESSAGE) {
            return new GenericDomainEventMessage<>(type, aggregateIdentifier, sequenceNumber,
                                                   new SerializedMessage<>(identifier, serializedPayload,
                                                                           serializedMetaData, serializer),
                                                   Instant.parse(timestamp));
        } else {
            return new GenericEventMessage<>(
                    new SerializedMessage<>(identifier, serializedPayload, serializedMetaData, serializer),
                    Instant.parse(timestamp));
        }
    }
}
