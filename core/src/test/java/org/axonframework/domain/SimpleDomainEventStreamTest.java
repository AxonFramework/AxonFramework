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

import org.junit.*;

import java.util.NoSuchElementException;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class SimpleDomainEventStreamTest {

    @Test
    public void testPeek() {
        StubDomainEvent event1 = new StubDomainEvent();
        StubDomainEvent event2 = new StubDomainEvent();
        SimpleDomainEventStream testSubject = new SimpleDomainEventStream(event1, event2);
        assertSame(event1, testSubject.peek());
        assertSame(event1, testSubject.peek());

    }

    @Test
    public void testPeek_EmptyStream() {
        SimpleDomainEventStream testSubject = new SimpleDomainEventStream();
        assertFalse(testSubject.hasNext());
        try {
            testSubject.peek();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // what we expect
        }
    }

    @Test
    public void testNextAndHasNext() {
        StubDomainEvent event1 = new StubDomainEvent();
        StubDomainEvent event2 = new StubDomainEvent();
        SimpleDomainEventStream testSubject = new SimpleDomainEventStream(event1, event2);
        assertTrue(testSubject.hasNext());
        assertSame(event1, testSubject.next());
        assertTrue(testSubject.hasNext());
        assertSame(event2, testSubject.next());
        assertFalse(testSubject.hasNext());
    }

    @Test(expected = NoSuchElementException.class)
    public void testNext_ReadBeyondEnd() {
        StubDomainEvent event1 = new StubDomainEvent();
        SimpleDomainEventStream testSubject = new SimpleDomainEventStream(event1);
        testSubject.next();
        testSubject.next();
    }

}
