/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.GenericDomainEventEntry;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import java.time.Instant;

import static org.junit.Assert.*;

public class EqualRevisionPredicateTest {

    private static final String PAYLOAD = "payload", AGGREGATE = "aggregate", TYPE = "type", METADATA = "metadata";
    private EqualRevisionPredicate testSubject;

    @Before
    public void setUp() {
        testSubject = new EqualRevisionPredicate(new AnnotationRevisionResolver(), XStreamSerializer.builder().build());
    }

    @Test
    public void testSameRevisionForAggregateAndPayload() {
        assertTrue(testSubject.test(createEntry(WithAnnotationAggregate.class.getName(), "2.3-TEST")));
    }

    @Test
    public void testDifferentRevisionsForAggregateAndPayload() {
        assertFalse(testSubject.test(createEntry(WithAnnotationAggregate.class.getName(), "2.3-TEST-DIFFERENT")));
    }

    @Test
    public void testNoRevisionForAggregateAndPayload() {
        assertTrue(testSubject.test(createEntry(WithoutAnnotationAggregate.class.getName())));
    }

    @Test
    public void testNoRevisionForPayload() {
        assertFalse(testSubject.test(createEntry(WithAnnotationAggregate.class.getName())));
    }

    @Test
    public void testNoRevisionForAggregate() {
        assertFalse(testSubject.test(createEntry(WithoutAnnotationAggregate.class.getName(), "2.3-TEST")));
    }

    private static DomainEventData<?> createEntry(String payloadType) {
        return createEntry(payloadType, null);
    }

    private static DomainEventData<?> createEntry(String payloadType, String payloadRevision) {
        return new GenericDomainEventEntry<>(TYPE, AGGREGATE, 0,
                                             IdentifierFactory.getInstance().generateIdentifier(), Instant.now(),
                                             payloadType, payloadRevision, PAYLOAD, METADATA);
    }

    @Revision("2.3-TEST")
    private class WithAnnotationAggregate {

    }

    private class WithoutAnnotationAggregate {

    }
}
