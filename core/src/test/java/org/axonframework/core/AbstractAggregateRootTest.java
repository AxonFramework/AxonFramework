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

import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractAggregateRootTest {

    private SimpleAggregateRoot testSubject;

    @Test
    public void testInitializeWithEvents() {
        UUID identifier = UUID.randomUUID();
        testSubject = new SimpleAggregateRoot(identifier);
        testSubject.initializeState(new SimpleEventStream(new StubDomainEvent(identifier, 243)));

        assertEquals(identifier, testSubject.getIdentifier());
        assertEquals(0, testSubject.getUncommittedEventCount());
        assertEquals(1, testSubject.invocationCount);
        assertEquals(new Long(243), testSubject.getLastCommittedEventSequenceNumber());
    }

    @Test
    public void testApplyEvent() {
        testSubject = new SimpleAggregateRoot();

        assertNotNull(testSubject.getIdentifier());
        assertEquals(0, testSubject.getUncommittedEventCount());
        assertEquals(null, testSubject.getLastCommittedEventSequenceNumber());

        testSubject.apply(new StubDomainEvent());

        assertEquals(1, testSubject.invocationCount);
        assertEquals(1, testSubject.getUncommittedEventCount());
        assertEquals(null, testSubject.getLastCommittedEventSequenceNumber());

        testSubject.commitEvents();
        assertEquals(new Long(0), testSubject.getLastCommittedEventSequenceNumber());
        assertFalse(testSubject.getUncommittedEvents().hasNext());
    }

    private static class SimpleAggregateRoot extends AbstractAggregateRoot {

        private int invocationCount;

        private SimpleAggregateRoot() {
            super();
        }

        private SimpleAggregateRoot(UUID identifier) {
            super(identifier);
        }

        @Override
        protected void handle(DomainEvent event) {
            this.invocationCount++;
        }
    }

}
