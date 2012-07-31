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

package org.axonframework.eventstore.fs;

import org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventData;

import java.io.DataInput;
import java.io.IOException;

/**
 * Reads events from the file system returns an event in a raw form as {@link SerializedDomainEventData}.
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
    public SerializedDomainEventData readEventMessage() throws IOException {
        // type byte for future use
        in.readByte();
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

        return new SimpleSerializedDomainEventData(identifier,
                                        aggregateIdentifier,
                                        sequenceNumber,
                                        timestamp,
                                        payloadType,
                                        payloadRevision,
                                        payload, metaData);
    }
}
