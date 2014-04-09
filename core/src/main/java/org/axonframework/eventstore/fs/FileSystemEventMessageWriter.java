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
import org.axonframework.serializer.MessageSerializer;
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

    private final MessageSerializer messageSerializer;
    private final DataOutput out;

    /**
     * Creates a new EventMessageWriter writing data to the specified underlying <code>output</code>.
     *
     * @param output     the underlying output
     * @param serializer The serializer to deserialize payload and metadata with
     */
    public FileSystemEventMessageWriter(DataOutput output, Serializer serializer) {
        this.out = output;
        this.messageSerializer = new MessageSerializer(serializer);
    }

    /**
     * Writes the given <code>eventMessage</code> to the underling output.
     *
     * @param eventMessage the EventMessage to write to the underlying output
     * @throws java.io.IOException when any exception occurs writing to the underlying stream
     */
    public void writeEventMessage(DomainEventMessage eventMessage) throws IOException {
        // type byte for future use
        out.write(0);
        out.writeUTF(eventMessage.getIdentifier());
        out.writeUTF(eventMessage.getTimestamp().toString());
        out.writeUTF(eventMessage.getAggregateIdentifier().toString());
        out.writeLong(eventMessage.getSequenceNumber());

        SerializedObject<byte[]> serializedPayload = messageSerializer.serializePayload(eventMessage, byte[].class);
        SerializedObject<byte[]> serializedMetaData = messageSerializer.serializeMetaData(eventMessage, byte[].class);

        out.writeUTF(serializedPayload.getType().getName());
        String revision = serializedPayload.getType().getRevision();
        out.writeUTF(revision == null ? "" : revision);
        out.writeInt(serializedPayload.getData().length);
        out.write(serializedPayload.getData());
        out.writeInt(serializedMetaData.getData().length);
        out.write(serializedMetaData.getData());
    }
}
