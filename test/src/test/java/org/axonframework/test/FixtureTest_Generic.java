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

package org.axonframework.test;

import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventStoreException;
import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class FixtureTest_Generic {

    private FixtureConfiguration fixture;

    @Before
    public void setUp() {
        fixture = Fixtures.newGivenWhenThenFixture();
    }

    @Test
    public void testAggregateIdentifier_DefaultsToUuidAsString() {
        assertNotNull(fixture.getAggregateIdentifier());
        // this must work
        UUID.fromString(fixture.getAggregateIdentifier().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAggregateIdentifier_MustHaveToStringMethod() {
        fixture.setAggregateIdentifier(new Object() {
        });
    }

    @Test
    public void testAggregateIdentifier_Custom() {
        fixture.setAggregateIdentifier("My value");
        assertEquals("My value", fixture.getAggregateIdentifier());
    }

    @Test(expected = EventStoreException.class)
    public void testFixtureGeneratesExceptionOnWrongEvents_DifferentAggregateIdentifiers() {
        fixture.getEventStore().appendEvents("whatever", new SimpleDomainEventStream(
                new GenericDomainEventMessage<StubDomainEvent>(UUID.randomUUID(), 0, new StubDomainEvent()),
                new GenericDomainEventMessage<StubDomainEvent>(UUID.randomUUID(), 0, new StubDomainEvent())));
    }

    @Test(expected = EventStoreException.class)
    public void testFixtureGeneratesExceptionOnWrongEvents_WrongSequence() {
        UUID identifier = UUID.randomUUID();
        fixture.getEventStore().appendEvents("whatever", new SimpleDomainEventStream(
                new GenericDomainEventMessage<StubDomainEvent>(identifier, 0, new StubDomainEvent()),
                new GenericDomainEventMessage<StubDomainEvent>(identifier, 2, new StubDomainEvent())));
    }

    private class StubDomainEvent {

        public StubDomainEvent() {
        }
    }
}
