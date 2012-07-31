/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.io;

import org.axonframework.domain.EventMessage;
import org.axonframework.serializer.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedEventMessage;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.joda.time.DateTime;

import java.io.DataInput;
import java.io.IOException;

/**
 * Reader that reads EventMessage instances written to the underlying input. Typically, these messages have been
 * written using a {@link EventMessageWriter}. This reader distinguishes between DomainEventMessage and regular
 * EventMessage implementations and will reconstruct an instance implementing that same interface when reading.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class EventMessageReader {

    private final Serializer serializer;
    private final DataInput in;

    /**
     * Creates a new EventMessageReader that reads the data from the given <code>input</code> and deserializes payload
     * and meta data using the given <code>serializer</code>.
     *
     * @param input      The input providing access to the written data
     * @param serializer The serializer to deserialize payload and meta data with
     */
    public EventMessageReader(DataInput input, Serializer serializer) {
        this.in = input;
        this.serializer = serializer;
    }

    /**
     * Reads an EventMessage from the underlying input. If the written event was a DomainEventMessage, an instance of
     * DomainEventMessage is returned.
     *
     * @param <T> The type of payload expected to be in the returned EventMessage. This is not checked at runtime!
     * @return an EventMessage representing the message originally written.
     *
     * @throws IOException          when an error occurs reading from the underlying input
     * @throws java.io.EOFException when the end of the stream was reached before the message was entirely read
     */
    public <T> EventMessage<T> readEventMessage() throws IOException {
        EventMessageType messageType = EventMessageType.fromTypeByte(in.readByte());
        String identifier = in.readUTF();
        String timestamp = in.readUTF();
        String aggregateIdentifier = null;
        long sequenceNumber = 0;
        if (messageType == EventMessageType.DOMAIN_EVENT_MESSAGE) {
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
        SimpleSerializedObject<byte[]> serializedPayload = new SimpleSerializedObject<byte[]>(payload,
                                                                                              byte[].class,
                                                                                              payloadType,
                                                                                              payloadRevision);
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<byte[]>(metaData, byte[].class);

        SerializedEventMessage<T> message = new SerializedEventMessage<T>(identifier, new DateTime(timestamp),
                                                                          serializedPayload, serializedMetaData,
                                                                          serializer);
        if (messageType == EventMessageType.DOMAIN_EVENT_MESSAGE) {
            return new SerializedDomainEventMessage<T>(message, aggregateIdentifier, sequenceNumber);
        }
        return message;
    }
}
