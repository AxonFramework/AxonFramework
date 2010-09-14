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

package org.axonframework.domain;

import org.joda.time.DateTimeUtils;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

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
        event.setAggregateIdentifier(AggregateIdentifierFactory.randomIdentifier());
        event.setAggregateIdentifier(AggregateIdentifierFactory.randomIdentifier());
    }

    @Test
    public void testDomainEventEquality() {
        DateTimeUtils.setCurrentMillisFixed(System.currentTimeMillis());
        Event eventWithoutAggrIdOrIdentifier = new StubDomainEvent();
        Event anotherEventWithoutAggrIdOrIdentifier = new StubDomainEvent();

        assertFalse(eventWithoutAggrIdOrIdentifier.equals(anotherEventWithoutAggrIdOrIdentifier));

        AggregateIdentifier aggregateIdentifier = AggregateIdentifierFactory.randomIdentifier();
        Event fullyInitializedEvent = new StubDomainEvent(aggregateIdentifier, 1);
        Event anotherFullyInitializedEvent = new StubDomainEvent(aggregateIdentifier, 1);

        assertFalse(fullyInitializedEvent.equals(anotherFullyInitializedEvent));

        Event eventWithTimestamp = new StubDomainEvent(aggregateIdentifier, 1);
        DateTimeUtils.setCurrentMillisFixed(System.currentTimeMillis() + 1000);
        Event eventWithOtherTimeStamp = new StubDomainEvent(aggregateIdentifier, 1);

        assertFalse(eventWithTimestamp.equals(eventWithOtherTimeStamp));

        assertFalse(eventWithOtherTimeStamp.equals(new Object()));

        Event eventWithoutAggregateIdentifier = new StubDomainEvent(2);

        assertFalse(fullyInitializedEvent.equals(eventWithoutAggregateIdentifier));
        assertFalse(eventWithoutAggregateIdentifier.equals(fullyInitializedEvent));
        assertFalse(new StubDomainEvent(AggregateIdentifierFactory.randomIdentifier()).equals(new StubDomainEvent(
                AggregateIdentifierFactory.randomIdentifier())));

        assertFalse(new StubDomainEvent(aggregateIdentifier, 1).equals(new StubDomainEvent(aggregateIdentifier, 2)));
    }

    @Test
    public void testDomainEventEquality_WithClones() throws ClassNotFoundException, IOException {
        AggregateIdentifier aggregateIdentifier = AggregateIdentifierFactory.randomIdentifier();
        StubDomainEvent someEvent = new StubDomainEvent();
        StubDomainEvent eventClone = copy(someEvent);
        assertTrue(someEvent.equals(eventClone));
        assertTrue(eventClone.equals(someEvent));

        someEvent.setAggregateIdentifier(aggregateIdentifier);
        assertFalse(someEvent.equals(eventClone));
        assertFalse(eventClone.equals(someEvent));

        eventClone.setAggregateIdentifier(aggregateIdentifier);
        assertTrue(someEvent.equals(eventClone));
        assertTrue(eventClone.equals(someEvent));

        someEvent.setSequenceNumber(1);
        assertFalse(someEvent.equals(eventClone));
        assertFalse(eventClone.equals(someEvent));

        eventClone.setSequenceNumber(1);
        assertTrue(someEvent.equals(eventClone));
        assertTrue(eventClone.equals(someEvent));
    }

    private StubDomainEvent copy(StubDomainEvent someEvent) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(someEvent);
        return (StubDomainEvent) new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())).readObject();
    }

    @Test
    public void testHashCode() {
        DomainEvent event = new StubDomainEvent();
        int hashCode1 = event.hashCode();

        event.setAggregateIdentifier(AggregateIdentifierFactory.randomIdentifier());
        assertEquals(hashCode1, event.hashCode());

        event.setSequenceNumber(1);
        assertEquals(hashCode1, event.hashCode());
    }

}
