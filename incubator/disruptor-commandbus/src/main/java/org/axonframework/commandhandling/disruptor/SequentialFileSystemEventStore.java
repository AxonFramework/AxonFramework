/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandhandling.disruptor;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.LazyDeserializingObject;
import org.axonframework.eventstore.SerializedDomainEventMessage;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.joda.time.DateTime;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Allard Buijze
 */
public class SequentialFileSystemEventStore implements SnapshotEventStore {

    private static final String FILE_NAME = "/tmp/trader-event.txt";
    private final ObjectOutputStream os;
    private Serializer eventSerializer;

    public SequentialFileSystemEventStore(Serializer eventSerializer) {
        this.eventSerializer = eventSerializer;
        try {
            os = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(FILE_NAME),
                                                                 1024 * 1024 * 4));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void appendEvents(String type, DomainEventStream events) {
        try {
            while (events.hasNext()) {
                DomainEventMessage event = events.next();
                os.writeUTF(event.getIdentifier());
                os.writeUTF(type);
                os.writeUTF(event.getAggregateIdentifier().toString());
                os.writeLong(event.getSequenceNumber());
                os.writeUTF(event.getTimestamp().toString());
                SerializedObject<byte[]> serialized = eventSerializer.serialize(event, byte[].class);
                os.writeUTF(serialized.getType().getName());
                os.writeInt(serialized.getType().getRevision());
                os.writeInt(serialized.getData().length);
                os.write(serialized.getData());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DomainEventStream readEvents(String type, Object identifier) {
        List<DomainEventMessage> domainEvents = new ArrayList<DomainEventMessage>();
        ObjectInputStream ois;
        try {
            os.flush();
            ois = new ObjectInputStream(new FileInputStream(FILE_NAME));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            while (true) {
                try {
                    String eventIdentifier = ois.readUTF();
                    String actualType = ois.readUTF();
                    String actualIdentifier = ois.readUTF();
                    long sequenceNumber = ois.readLong();
                    String timestamp = ois.readUTF();
                    String payloadType = ois.readUTF();
                    int payloadRevision = ois.readInt();
                    int length = ois.readInt();
                    byte[] serializedEvent = new byte[length];
                    ois.readFully(serializedEvent);
                    if (type.equals(actualType) && identifier.toString().equals(actualIdentifier)) {
                        domainEvents.add(new SerializedDomainEventMessage<Object>(
                                eventIdentifier,
                                actualIdentifier,
                                sequenceNumber,
                                new DateTime(timestamp),
                                new LazyDeserializingObject<Object>(
                                        new SimpleSerializedObject<byte[]>(serializedEvent, byte[].class,
                                                                           payloadType, payloadRevision),
                                        eventSerializer),
                                null
                        ));
                    }
                } catch (EOFException e) {
                    break;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            try {
                ois.close();
            } catch (IOException e) {
                // whatever
            }
        }
        return new SimpleDomainEventStream(domainEvents);
    }

    @Override
    public void appendSnapshotEvent(String type, DomainEventMessage snapshotEvent) {
        // ignored for the moment.
    }
}
