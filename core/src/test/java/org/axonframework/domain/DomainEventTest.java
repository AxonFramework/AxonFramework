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

package org.axonframework.domain;

import org.apache.commons.codec.binary.Base64;
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
        assertNull(event.getAggregateVersion());
    }

    @Test(expected = IllegalStateException.class)
    public void testCannotAddSequenceNumberTwice() {
        DomainEvent event = new StubDomainEvent();
        event.setSequenceNumber(1);
        assertEquals(Long.valueOf(1), event.getSequenceNumber());
        assertEquals(Long.valueOf(1), event.getAggregateVersion());
        event.setSequenceNumber(1);
    }

    @Test(expected = IllegalStateException.class)
    public void testCannotSetAggregateIdentifierTwice() {
        DomainEvent event = new StubDomainEvent();
        event.setAggregateIdentifier(new UUIDAggregateIdentifier());
        event.setAggregateIdentifier(new UUIDAggregateIdentifier());
    }

    @Test
    public void testDomainEventEquality() {
        DateTimeUtils.setCurrentMillisFixed(System.currentTimeMillis());
        Event eventWithoutAggrIdOrIdentifier = new StubDomainEvent();
        Event anotherEventWithoutAggrIdOrIdentifier = new StubDomainEvent();

        assertFalse(eventWithoutAggrIdOrIdentifier.equals(anotherEventWithoutAggrIdOrIdentifier));

        AggregateIdentifier aggregateIdentifier = new UUIDAggregateIdentifier();
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
        assertFalse(new StubDomainEvent(new UUIDAggregateIdentifier()).equals(new StubDomainEvent(
                new UUIDAggregateIdentifier())));

        assertFalse(new StubDomainEvent(aggregateIdentifier, 1).equals(new StubDomainEvent(aggregateIdentifier, 2)));
    }

    @Test
    public void testDomainEventEquality_WithClones() throws ClassNotFoundException, IOException {
        AggregateIdentifier aggregateIdentifier = new UUIDAggregateIdentifier();
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
    public void testSerialization_pre_v1_2() throws IOException, ClassNotFoundException {
        String encodedEvent = "rO0ABXNyAChvcmcuYXhvbmZyYW1ld29yay5kb21haW4uU3R1YkRvbWFpbkV2ZW50C5VVQ6Wrk+YCAAB4cgAkb3JnLmF4b25mcmFtZXdvcmsuZG9tYWluLkRvbWFpbkV2ZW50dUIXKIlmJMUCAAJMABNhZ2dyZWdhdGVJZGVudGlmaWVydAAuTG9yZy9heG9uZnJhbWV3b3JrL2RvbWFpbi9BZ2dyZWdhdGVJZGVudGlmaWVyO0wADnNlcXVlbmNlTnVtYmVydAAQTGphdmEvbGFuZy9Mb25nO3hyACJvcmcuYXhvbmZyYW1ld29yay5kb21haW4uRXZlbnRCYXNlc/AiSXvH+XgCAAJKAA1ldmVudFJldmlzaW9uTAAIbWV0YURhdGF0AC9Mb3JnL2F4b25mcmFtZXdvcmsvZG9tYWluL011dGFibGVFdmVudE1ldGFEYXRhO3hwAAAAAAAAAABzcgAtb3JnLmF4b25mcmFtZXdvcmsuZG9tYWluLk11dGFibGVFdmVudE1ldGFEYXRhWFQhM718LV8CAAFMAAZ2YWx1ZXN0AA9MamF2YS91dGlsL01hcDt4cHNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAx3CAAAABAAAAACdAAKX3RpbWVzdGFtcHNyABZvcmcuam9kYS50aW1lLkRhdGVUaW1luDx4ZGpb3fkCAAB4cgAfb3JnLmpvZGEudGltZS5iYXNlLkJhc2VEYXRlVGltZf//+eFPXS6jAgACSgAHaU1pbGxpc0wAC2lDaHJvbm9sb2d5dAAaTG9yZy9qb2RhL3RpbWUvQ2hyb25vbG9neTt4cAAAATHhGnDec3IAJ29yZy5qb2RhLnRpbWUuY2hyb25vLklTT0Nocm9ub2xvZ3kkU3R1YqnIEWZxN1AnAwAAeHBzcgAfb3JnLmpvZGEudGltZS5EYXRlVGltZVpvbmUkU3R1YqYvAZp8MhrjAwAAeHB3DwANRXVyb3BlL0Jlcmxpbnh4dAALX2lkZW50aWZpZXJzcgAOamF2YS51dGlsLlVVSUS8mQP3mG2FLwIAAkoADGxlYXN0U2lnQml0c0oAC21vc3RTaWdCaXRzeHCY56byHC785l9j5+K9OE6BeHBw";
        ByteArrayInputStream bis = new ByteArrayInputStream(Base64.decodeBase64(encodedEvent));
        ObjectInputStream ois = new ObjectInputStream(bis);
        DomainEvent event = (DomainEvent) ois.readObject();

        assertNotNull(event);
    }

    @Test
    public void testHashCode() {
        DomainEvent event = new StubDomainEvent();
        int hashCode1 = event.hashCode();

        event.setAggregateIdentifier(new UUIDAggregateIdentifier());
        assertEquals(hashCode1, event.hashCode());

        event.setSequenceNumber(1);
        assertEquals(hashCode1, event.hashCode());
    }
}
