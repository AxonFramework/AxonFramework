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

package org.axonframework.eventhandling.async;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link PropertySequencingPolicy}.
 *
 * @author Nils Christian Ehmke
 */
@DisplayName("Unit-Test for the PropertySequencingPolicy")
final class PropertySequencingPolicyTest {

    @Test
    void propertyExtractorShouldReadCorrectValue() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyExtractor(TestEvent::getId)
                .build();

        assertEquals("42", sequencingPolicy.getSequenceIdentifierFor(newStubDomainEvent(new TestEvent("42"))));
    }

    @Test
    void propertyShouldReadCorrectValue() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyName("Id")
                .build();

        assertEquals("42", sequencingPolicy.getSequenceIdentifierFor(newStubDomainEvent(new TestEvent("42"))));
    }

    @Test
    void defaultFallbackShouldThrowException() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyName("Id")
                .build();

        assertThrows(IllegalArgumentException.class,
                     () -> sequencingPolicy.getSequenceIdentifierFor(newStubDomainEvent("42")));
    }

    @Test
    void fallbackShouldBeApplied() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyName("Id")
                .fallbackSequencingPolicy(SequentialPerAggregatePolicy.instance())
                .build();

        assertEquals("A", sequencingPolicy.getSequenceIdentifierFor(newStubDomainEvent("42")));
    }

    private DomainEventMessage<?> newStubDomainEvent(final Object payload) {
        return new GenericDomainEventMessage<>("type", "A", 0L, payload, MetaData.emptyInstance());
    }

    private static class TestEvent {

        private final String id;

        public TestEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
