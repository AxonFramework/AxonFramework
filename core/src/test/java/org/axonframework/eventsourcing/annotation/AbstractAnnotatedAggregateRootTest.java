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

package org.axonframework.eventsourcing.annotation;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractAnnotatedAggregateRootTest {

    private SimpleAggregateRoot testSubject;

    @Test
    public void testApplyEvent() {
        testSubject = new SimpleAggregateRoot();

        assertNotNull(testSubject.getIdentifier());
        assertEquals(1, testSubject.getUncommittedEventCount());
        // this proves that a newly added entity is also notified of an event
        assertEquals(1, testSubject.getEntity().invocationCount);

        testSubject.doSomething();

        assertEquals(2, testSubject.invocationCount);
        assertEquals(2, testSubject.getEntity().invocationCount);
    }

    @Test
    public void testInitializeWithIdentifier() {
        testSubject = new SimpleAggregateRoot(new UUIDAggregateIdentifier());
        assertEquals(0, testSubject.getUncommittedEventCount());
    }

    private static class SimpleAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private int invocationCount;
        private volatile SimpleEntity entity;

        private SimpleAggregateRoot() {
            apply(new StubDomainEvent());
        }

        private SimpleAggregateRoot(AggregateIdentifier identifier) {
            super(identifier);
        }

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
            if (entity == null) {
                entity = new SimpleEntity();
            }
        }

        public SimpleEntity getEntity() {
            return entity;
        }

        public void doSomething() {
            apply(new StubDomainEvent());
        }
    }

    private static class SimpleEntity extends AbstractAnnotatedEntity {

        private int invocationCount;

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
        }
    }
}
