/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core;

import org.joda.time.DateTimeUtils;
import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class DomainEventTest {

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void testInitializeDomainEvent() {
        DomainEvent event = new StubDomainEvent();
        assertNotNull(event.getEventIdentifier());
        assertNull(event.getAggregateIdentifier());
        assertNull(event.getSequenceNumber());
    }

    @Test(expected = IllegalStateException.class)
    public void testCannotAddSequenceNumberTwice() {
        DomainEvent event = new StubDomainEvent();
        event.setSequenceNumber(1);
        event.setSequenceNumber(1);
    }

    @Test(expected = IllegalStateException.class)
    public void testCannotSetAggregateIdentifierTwice() {
        DomainEvent event = new StubDomainEvent();
        event.setAggregateIdentifier(UUID.randomUUID());
        event.setAggregateIdentifier(UUID.randomUUID());
    }

    @Test
    public void testDomainEventEquality() {
        DateTimeUtils.setCurrentMillisFixed(System.currentTimeMillis());
        DomainEvent eventWithoutAggrIdOrIdentifier = new StubDomainEvent();
        DomainEvent anotherEventWithoutAggrIdOrIdentifier = new StubDomainEvent();

        assertFalse(eventWithoutAggrIdOrIdentifier.equals(anotherEventWithoutAggrIdOrIdentifier));

        UUID aggregateIdentifier = UUID.randomUUID();
        DomainEvent fullyInitializedEvent = new StubDomainEvent(aggregateIdentifier, 1);
        DomainEvent anotherFullyInitializedEvent = new StubDomainEvent(aggregateIdentifier, 1);

        assertTrue(fullyInitializedEvent.equals(anotherFullyInitializedEvent));

        DomainEvent eventWithTimestamp = new StubDomainEvent(aggregateIdentifier, 1);
        DateTimeUtils.setCurrentMillisFixed(System.currentTimeMillis() + 1000);
        DomainEvent eventWithOtherTimeStamp = new StubDomainEvent(aggregateIdentifier, 1);

        assertFalse(eventWithTimestamp.equals(eventWithOtherTimeStamp));

        assertFalse(eventWithOtherTimeStamp.equals(new Object()));

        DomainEvent eventWithoutAggregateIdentifier = new StubDomainEvent(2);

        assertFalse(fullyInitializedEvent.equals(eventWithoutAggregateIdentifier));
        assertFalse(eventWithoutAggregateIdentifier.equals(fullyInitializedEvent));
        assertFalse(new StubDomainEvent(UUID.randomUUID()).equals(new StubDomainEvent(UUID.randomUUID())));

        assertFalse(new StubDomainEvent(aggregateIdentifier, 1).equals(new StubDomainEvent(aggregateIdentifier, 2)));
    }

    @Test
    public void testHashCode() {
        DomainEvent event = new StubDomainEvent();
        int hashCode1 = event.hashCode();

        event.setAggregateIdentifier(UUID.randomUUID());
        assertEquals(hashCode1, event.hashCode());

        event.setSequenceNumber(1);
        assertEquals(hashCode1, event.hashCode());
    }

}
