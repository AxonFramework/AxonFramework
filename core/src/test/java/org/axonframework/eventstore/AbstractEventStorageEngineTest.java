/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore;

import org.axonframework.domain.IdentifierFactory;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.UnknownSerializedTypeException;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.time.Instant.now;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventStorageEngineTest {

    protected static final String PAYLOAD = "payload", AGGREGATE = "aggregate", TYPE = "type";
    protected static final MetaData METADATA = MetaData.emptyInstance();

    private EventStorageEngine testSubject;

    @Test(expected = UnknownSerializedTypeException.class)
    @SuppressWarnings("unchecked")
    public void testUnknownSerializedTypeCausesException() {
        Serializer serializer = Mockito.spy(new XStreamSerializer());
        Mockito.when(serializer.serialize(Matchers.anyString(), Matchers
                .any())).thenAnswer((Answer<SerializedObject>) invocation -> {
            SerializedObject<?> serializedObject = (SerializedObject<?>) invocation.callRealMethod();
            return new SimpleSerializedObject(serializedObject.getData(), serializedObject.getContentType(),
                                                "unknown", serializedObject.getType().getRevision());
        });
        setSerializer(serializer);
        testSubject.appendEvents(createEvents(1));
        testSubject.readEvents(AGGREGATE);
    }



    protected List<DomainEventMessage<?>> createEvents(int numberOfEvents) {
        return IntStream.range(0, numberOfEvents).mapToObj(
                (sequenceNumber) -> createEvent(TYPE, IdentifierFactory.getInstance().generateIdentifier(), AGGREGATE,
                                                sequenceNumber, PAYLOAD + sequenceNumber, METADATA))
                .collect(Collectors.toList());
    }

    protected DomainEventMessage<String> createEvent(long sequenceNumber) {
        return createEvent(AGGREGATE, sequenceNumber);
    }

    protected DomainEventMessage<String> createEvent(String aggregateId, long sequenceNumber) {
        return createEvent(aggregateId, sequenceNumber, PAYLOAD);
    }

    protected DomainEventMessage<String> createEvent(String aggregateId, long sequenceNumber, String payload) {
        return createEvent(TYPE, IdentifierFactory.getInstance().generateIdentifier(), aggregateId, sequenceNumber,
                           payload, METADATA);
    }

    protected DomainEventMessage<String> createEvent(String eventId, String aggregateId, long sequenceNumber) {
        return createEvent(TYPE, eventId, aggregateId, sequenceNumber, PAYLOAD, METADATA);
    }

    protected DomainEventMessage<String> createEvent(String type, String eventId, String aggregateId,
                                                     long sequenceNumber, String payload, MetaData metaData) {
        return new GenericDomainEventMessage<>(type, aggregateId, sequenceNumber,
                                               new GenericMessage<>(eventId, payload, metaData), now());
    }

    protected abstract void setSerializer(Serializer serializer);

    protected EventStorageEngine testSubject() {
        return testSubject;
    }

    protected void setTestSubject(EventStorageEngine testSubject) {
        this.testSubject = testSubject;
    }
}
