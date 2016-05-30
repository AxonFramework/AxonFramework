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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.metadata.MetaData;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.time.Instant.now;

/**
 * @author Rene de Waele
 */
public class EventStoreTestUtils {

    public static final String PAYLOAD = "payload", AGGREGATE = "aggregate", TYPE = "type";
    public static final MetaData METADATA = MetaData.emptyInstance();

    public static List<DomainEventMessage<?>> createEvents(int numberOfEvents) {
        return IntStream.range(0, numberOfEvents).mapToObj(
                sequenceNumber -> createEvent(TYPE, IdentifierFactory.getInstance().generateIdentifier(), AGGREGATE,
                                              sequenceNumber, PAYLOAD + sequenceNumber, METADATA))
                .collect(Collectors.toList());
    }

    public static DomainEventMessage<String> createEvent() {
        return createEvent(0);
    }

    public static DomainEventMessage<String> createEvent(long sequenceNumber) {
        return createEvent(AGGREGATE, sequenceNumber);
    }

    public static DomainEventMessage<String> createEvent(String aggregateId, long sequenceNumber) {
        return createEvent(aggregateId, sequenceNumber, PAYLOAD);
    }

    public static DomainEventMessage<String> createEvent(String aggregateId, long sequenceNumber, String payload) {
        return createEvent(TYPE, IdentifierFactory.getInstance().generateIdentifier(), aggregateId, sequenceNumber,
                           payload, METADATA);
    }

    public static DomainEventMessage<String> createEvent(String eventId, String aggregateId, long sequenceNumber) {
        return createEvent(TYPE, eventId, aggregateId, sequenceNumber, PAYLOAD, METADATA);
    }

    public static DomainEventMessage<String> createEvent(String type, String eventId, String aggregateId,
                                                         long sequenceNumber, String payload, MetaData metaData) {
        return new GenericDomainEventMessage<>(type, aggregateId, sequenceNumber,
                                               new GenericMessage<>(eventId, payload, metaData), now());
    }

    private EventStoreTestUtils() {
    }

}
