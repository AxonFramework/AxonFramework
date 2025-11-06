/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventsourcing.snapshotting;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.eventhandling.DomainEventData;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.conversion.Serializer;
import org.axonframework.conversion.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link RevisionSnapshotFilter}.
 *
 * @author Steven van Beelen
 */
class RevisionSnapshotFilterTest {

    private static final String EXPECTED_REVISION = "LET ME IN";

    private final Serializer serializer = JacksonSerializer.defaultSerializer();

    @Test
    void allowsDomainEventDataContainingTheAllowedAggregateTypeAndRevision() {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(RightAggregateTypeAndRevision.class.getSimpleName())
                                      .revision(EXPECTED_REVISION)
                                      .build();

        DomainEventMessage snapshotEvent = new GenericDomainEventMessage(
                RightAggregateTypeAndRevision.class.getName(), "some-aggregate-id", 0,
                new MessageType("snapshot"), new RightAggregateTypeAndRevision("some-state")
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        assertTrue(testSubject.allow(testDomainEventData));
    }

    @Test
    void allowsDomainEventDataContainingTheWrongAggregateTypeAndAllowedRevision() {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(RightAggregateTypeAndRevision.class.getSimpleName())
                                      .revision(EXPECTED_REVISION)
                                      .build();

        DomainEventMessage snapshotEvent = new GenericDomainEventMessage(
                WrongAggregateType.class.getName(), "some-aggregate-id", 0,
                new MessageType("snapshot"), new WrongAggregateType("some-state")
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        assertTrue(testSubject.allow(testDomainEventData));
    }

    @Test
    void disallowsDomainEventDataContainingTheAllowedAggregateTypeAndWrongRevision() {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(RightAggregateTypeAndWrongRevision.class)
                                      .revision(EXPECTED_REVISION)
                                      .build();

        DomainEventMessage snapshotEvent = new GenericDomainEventMessage(
                RightAggregateTypeAndWrongRevision.class.getName(), "some-aggregate-id", 0,
                new MessageType("snapshot"), new RightAggregateTypeAndWrongRevision("some-state")
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        assertFalse(testSubject.allow(testDomainEventData));
    }

    @Test
    void buildWithNullOrEmptyTypeThrowsAxonConfigurationException() {
        RevisionSnapshotFilter.Builder builderTestSubject = RevisionSnapshotFilter.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.type(""));
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.type((String) null));
    }

    @Test
    void buildWithNullOrEmptyRevisionThrowsAxonConfigurationException() {
        RevisionSnapshotFilter.Builder builderTestSubject = RevisionSnapshotFilter.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.revision(""));
    }

    @Test
    void buildWithoutTypeThrowsAxonConfigurationException() {
        RevisionSnapshotFilter.Builder builderTestSubject = RevisionSnapshotFilter.builder()
                                                                                  .revision(EXPECTED_REVISION);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithBlankRevisionThrowsAxonConfigurationException() {
        assertThrows(AxonConfigurationException.class, () -> RevisionSnapshotFilter.builder()
                                                                                   .type(RightAggregateTypeAndRevision.class)
                                                                                   .revision("")
                                                                                   .build());
    }

    @Event(version = EXPECTED_REVISION)
    private record RightAggregateTypeAndRevision(String state) {

    }

    @Event(version = "some-other-revision")
    private record WrongAggregateType(String state) {

    }

    @Event(version = "some-other-revision")
    private record RightAggregateTypeAndWrongRevision(String state) {

    }
}