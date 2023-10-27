/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.utils;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class EventTestUtils {

    public static final String PAYLOAD = "payload";
    public static final String AGGREGATE = "aggregate";
    private static final String TYPE = "type";
    private static final MetaData METADATA = MetaData.emptyInstance();

    public static List<DomainEventMessage<?>> createEvents(int numberOfEvents) {
        return IntStream.range(0, numberOfEvents)
                        .mapToObj(sequenceNumber -> createEvent(TYPE,
                                                                IdentifierFactory.getInstance().generateIdentifier(),
                                                                AGGREGATE,
                                                                sequenceNumber,
                                                                PAYLOAD + sequenceNumber,
                                                                METADATA))
                        .collect(Collectors.toList());
    }

    public static List<DomainEventMessage<?>> createUUIDEvents(int numberOfEvents) {
        return IntStream.range(0, numberOfEvents).mapToObj(
                sequenceNumber -> createEvent(TYPE,
                                              IdentifierFactory.getInstance().generateIdentifier(),
                                              UUID.randomUUID().toString(),
                                              sequenceNumber,
                                              PAYLOAD + sequenceNumber,
                                              METADATA))
                        .collect(Collectors.toList());
    }

    public static DomainEventMessage<String> createEvent() {
        return createEvent(0);
    }

    public static DomainEventMessage<String> createEvent(long sequenceNumber) {
        return createEvent(AGGREGATE, sequenceNumber);
    }

    public static DomainEventMessage<String> createEvent(long sequenceNumber, Instant timestamp) {
        return new GenericDomainEventMessage<>(TYPE, AGGREGATE, sequenceNumber, PAYLOAD, METADATA,
                                               IdentifierFactory.getInstance().generateIdentifier(), timestamp);
    }

    public static DomainEventMessage<String> createEvent(String aggregateId, long sequenceNumber) {
        return createEvent(aggregateId, sequenceNumber, PAYLOAD);
    }

    public static DomainEventMessage<String> createEvent(String aggregateId, long sequenceNumber, String payload) {
        return createEvent(TYPE,
                           IdentifierFactory.getInstance().generateIdentifier(),
                           aggregateId,
                           sequenceNumber,
                           payload,
                           METADATA);
    }

    public static DomainEventMessage<String> createEvent(String eventId, String aggregateId, long sequenceNumber) {
        return createEvent(TYPE, eventId, aggregateId, sequenceNumber, PAYLOAD, METADATA);
    }

    public static DomainEventMessage<String> createEvent(String type,
                                                         String eventId,
                                                         String aggregateId,
                                                         long sequenceNumber,
                                                         String payload,
                                                         MetaData metaData) {
        return new GenericDomainEventMessage<>(type,
                                               aggregateId,
                                               sequenceNumber,
                                               payload,
                                               metaData,
                                               eventId,
                                               GenericDomainEventMessage.clock.instant());
    }

    private EventTestUtils() {
    }
}
