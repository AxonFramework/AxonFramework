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

package org.axonframework.eventsourcing;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractEventSourcedAggregateRootTest {

    private SimpleAggregateRoot testSubject;

    @Test
    public void testInitializeWithEvents() {
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        testSubject = new SimpleAggregateRoot(identifier);
        testSubject.initializeState(new SimpleDomainEventStream(new StubDomainEvent(identifier, 243)));

        assertEquals(identifier, testSubject.getIdentifier());
        assertEquals(0, testSubject.getUncommittedEventCount());
        assertEquals(1, testSubject.invocationCount);
        assertEquals(new Long(243), testSubject.getVersion());
    }

    @Test
    public void testApplyEvent() {
        testSubject = new SimpleAggregateRoot();

        assertNotNull(testSubject.getIdentifier());
        assertEquals(0, testSubject.getUncommittedEventCount());
        assertEquals(null, testSubject.getVersion());

        testSubject.apply(new StubDomainEvent());

        assertEquals(1, testSubject.invocationCount);
        assertEquals(1, testSubject.getUncommittedEventCount());
        assertEquals(null, testSubject.getVersion());

        testSubject.commitEvents();
        assertEquals(new Long(0), testSubject.getVersion());
        assertFalse(testSubject.getUncommittedEvents().hasNext());
    }

    private static class SimpleAggregateRoot extends AbstractEventSourcedAggregateRoot {

        private int invocationCount;

        private SimpleAggregateRoot() {
            super();
        }

        private SimpleAggregateRoot(AggregateIdentifier identifier) {
            super(identifier);
        }

        @Override
        protected void handle(DomainEvent event) {
            this.invocationCount++;
        }
    }

}
