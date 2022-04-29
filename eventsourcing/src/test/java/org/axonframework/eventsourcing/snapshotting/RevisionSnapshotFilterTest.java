/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.eventsourcing.snapshotting;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.axonframework.eventsourcing.utils.TestSerializer;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link RevisionSnapshotFilter}.
 *
 * @author Steven van Beelen
 */
class RevisionSnapshotFilterTest {

    private static final String EXPECTED_REVISION = "LET ME IN";

    private final Serializer serializer = TestSerializer.xStreamSerializer();

    @Test
    void testAllowsDomainEventDataContainingTheAllowedAggregateTypeAndRevision() {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(RightAggregateTypeAndRevision.class.getSimpleName())
                                      .revision(EXPECTED_REVISION)
                                      .build();

        DomainEventMessage<RightAggregateTypeAndRevision> snapshotEvent = new GenericDomainEventMessage<>(
                RightAggregateTypeAndRevision.class.getName(),
                "some-aggregate-id",
                0,
                new RightAggregateTypeAndRevision("some-state")
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        assertTrue(testSubject.allow(testDomainEventData));
    }

    @Test
    void testAllowsDomainEventDataContainingTheWrongAggregateTypeAndAllowedRevision() {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(RightAggregateTypeAndRevision.class.getSimpleName())
                                      .revision(EXPECTED_REVISION)
                                      .build();

        DomainEventMessage<WrongAggregateType> snapshotEvent = new GenericDomainEventMessage<>(
                WrongAggregateType.class.getName(), "some-aggregate-id", 0, new WrongAggregateType("some-state")
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        assertTrue(testSubject.allow(testDomainEventData));
    }

    @Test
    void testDisallowsDomainEventDataContainingTheAllowedAggregateTypeAndWrongRevision() {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(RightAggregateTypeAndWrongRevision.class)
                                      .revision(EXPECTED_REVISION)
                                      .build();

        DomainEventMessage<RightAggregateTypeAndWrongRevision> snapshotEvent = new GenericDomainEventMessage<>(
                RightAggregateTypeAndWrongRevision.class.getName(), "some-aggregate-id", 0,
                new RightAggregateTypeAndWrongRevision("some-state")
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        assertFalse(testSubject.allow(testDomainEventData));
    }

    @Test
    void testBuildWithNullOrEmptyTypeThrowsAxonConfigurationException() {
        RevisionSnapshotFilter.Builder builderTestSubject = RevisionSnapshotFilter.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.type(""));
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.type((String) null));
    }
    
    @Test
    void testBuildWithoutTypeThrowsAxonConfigurationException() {
        RevisionSnapshotFilter.Builder builderTestSubject = RevisionSnapshotFilter.builder()
                                                                                  .revision(EXPECTED_REVISION);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Revision(EXPECTED_REVISION)
    private static class RightAggregateTypeAndRevision {

        private final String state;

        private RightAggregateTypeAndRevision(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }

    @Revision("some-other-revision")
    private static class WrongAggregateType {

        private final String state;

        private WrongAggregateType(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }

    @Revision("some-other-revision")
    private static class RightAggregateTypeAndWrongRevision {

        private final String state;

        private RightAggregateTypeAndWrongRevision(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }
}