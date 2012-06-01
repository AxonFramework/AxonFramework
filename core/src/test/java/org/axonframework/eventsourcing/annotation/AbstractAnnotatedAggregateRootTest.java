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

import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.junit.*;

import java.util.UUID;

import javax.persistence.Id;

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
        testSubject = new SimpleAggregateRoot(UUID.randomUUID());
        assertEquals(0, testSubject.getUncommittedEventCount());
    }

    @Test
    public void testIdentifierInitialization_LateInitialization() {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate();
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", aggregate.getUncommittedEvents().peek().getAggregateIdentifier());
    }

    @Test
    public void testIdentifierInitialization_JavaxPersistenceId() {
        JavaxPersistenceIdIdentifiedAggregate aggregate = new JavaxPersistenceIdIdentifiedAggregate();
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", aggregate.getUncommittedEvents().peek().getAggregateIdentifier());
    }

    private static class LateIdentifiedAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String aggregateIdentifier;

        private LateIdentifiedAggregate() {
            apply(new StubDomainEvent());
        }

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            aggregateIdentifier = "lateIdentifier";
        }
    }

private static class JavaxPersistenceIdIdentifiedAggregate extends AbstractAnnotatedAggregateRoot {

        @Id
        private String aggregateIdentifier;

        private JavaxPersistenceIdIdentifiedAggregate() {
            apply(new StubDomainEvent());
        }

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            aggregateIdentifier = "lateIdentifier";
        }
    }

    private static class SimpleAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private int invocationCount;
        private volatile SimpleEntity entity;
        private final UUID identifier;

        private SimpleAggregateRoot() {
            identifier = UUID.randomUUID();
            apply(new StubDomainEvent());
        }

        private SimpleAggregateRoot(UUID identifier) {
            this.identifier = identifier;
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

        @Override
        public UUID getIdentifier() {
            return identifier;
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
