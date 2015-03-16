/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.FixedValueParameterResolver;
import org.axonframework.common.annotation.MultiParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.annotation.Timestamp;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.joda.time.DateTime;
import org.junit.*;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        // the first applied event applies another one
        assertEquals(2, testSubject.getUncommittedEventCount());
        // this proves that a newly added entity is also notified of an event
        assertEquals(2, testSubject.getEntity().invocationCount);

        testSubject.doSomething();

        assertEquals(3, testSubject.invocationCount);
        assertEquals(3, testSubject.getEntity().invocationCount);

        // the nested handler must be invoked second
        assertFalse(testSubject.entity.appliedEvents.get(0).nested);
        assertTrue(testSubject.entity.appliedEvents.get(1).nested);
        assertFalse(testSubject.entity.appliedEvents.get(2).nested);

        DomainEventStream uncommittedEvents = testSubject.getUncommittedEvents();
        int i = 0;
        while (uncommittedEvents.hasNext()) {
            assertSame(testSubject.entity.appliedEvents.get(i), uncommittedEvents.next().getPayload());
            i++;
        }
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testInitializeWithIdentifier() {
        testSubject = new SimpleAggregateRoot(UUID.randomUUID());
        assertEquals(0, testSubject.getUncommittedEventCount());
    }

    @Test
    public void testIdentifierInitialization_LateInitialization() {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate(new StubDomainEvent(false));
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", aggregate.getUncommittedEvents().peek().getAggregateIdentifier());

        DomainEventStream uncommittedEvents = aggregate.getUncommittedEvents();
        assertFalse(((StubDomainEvent) uncommittedEvents.next().getPayload()).nested);
        assertTrue(((StubDomainEvent) uncommittedEvents.next().getPayload()).nested);
    }

    @Test
    public void testTimestampInReconstructionIsSameAsInitialTimestamp() {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate(new StubDomainEvent(false));
        DateTime firstTimestamp = aggregate.creationTime;

        LateIdentifiedAggregate aggregate2 = new LateIdentifiedAggregate();
        aggregate2.initializeState(aggregate.getUncommittedEvents());

        assertSame(aggregate2.creationTime, firstTimestamp);
    }

    @Test
    public void testIdentifierInitialization_JavaxPersistenceId() {
        JavaxPersistenceIdIdentifiedAggregate aggregate = new JavaxPersistenceIdIdentifiedAggregate();
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", aggregate.getUncommittedEvents().peek().getAggregateIdentifier());
    }

    @Test
    public void testSerializationSetsLiveStateToTrue() throws Exception {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate(new StubDomainEvent(false));
        aggregate.commitEvents();
        final XStreamSerializer serializer = new XStreamSerializer();
        SerializedObject<String> serialized = serializer.serialize(aggregate, String.class);

        LateIdentifiedAggregate deserializedAggregate = serializer.deserialize(serialized);
        assertTrue(deserializedAggregate.isLive());
    }

    @Test
    public void testAggregateRetrievesParameterResolverFactoryFromUnitOfWork() {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        uow.attachResource(ParameterResolverFactory.class.getName(), MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(CustomParameterAggregateRoot.class),
                (memberAnnotations, parameterType, parameterAnnotations) -> {
                    if (String.class.equals(parameterType)) {
                        return new FixedValueParameterResolver<>("It works");
                    }
                    return null;
                }));
        CustomParameterAggregateRoot aggregateRoot = new CustomParameterAggregateRoot();
        aggregateRoot.doSomething();

        assertEquals("It works", aggregateRoot.secondParameter);

        uow.rollback();
    }

    @Test
    public void testEventNotAppliedInReplayMode() {
        final UUID id = UUID.randomUUID();
        testSubject = new SimpleAggregateRoot(id);
        testSubject.initializeState(new SimpleDomainEventStream(
                new GenericDomainEventMessage<>(id.toString(), 0, new StubDomainEvent(false)),
                new GenericDomainEventMessage<>(id.toString(), 1, new StubDomainEvent(true))));

        assertEquals(0, testSubject.getUncommittedEventCount());
        assertEquals((Long) 1L, testSubject.getVersion());
        assertEquals(2, testSubject.invocationCount);

        // the nested handler must be invoked second
        assertFalse(testSubject.entity.appliedEvents.get(0).nested);
        assertTrue(testSubject.entity.appliedEvents.get(1).nested);
    }

    @Test
    public void testStateResetWhenAppliedEventCausesException() throws Exception {
        final UUID id = UUID.randomUUID();
        testSubject = new SimpleAggregateRoot(id) {
            @EventSourcingHandler
            public void myEventHandlerMethod(StubDomainEvent event) {
                super.myEventHandlerMethod(event);
                throw new RuntimeException("Mock");
            }
        };
        try {
            testSubject.doSomething();
            fail("Expected exception to have been propagated");
        } catch (RuntimeException e) {
            assertEquals(1, testSubject.getUncommittedEventCount());
        }
        Field field = AbstractEventSourcedAggregateRoot.class.getDeclaredField("eventsToApply");
        assertEquals(1, ((Collection) ReflectionUtils.getFieldValue(field, testSubject)).size());
        testSubject.commitEvents();
        assertEquals(0, ((Collection) ReflectionUtils.getFieldValue(field, testSubject)).size());
    }

    private static class LateIdentifiedAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String aggregateIdentifier;

        private DateTime creationTime;

        public LateIdentifiedAggregate() {
        }

        private LateIdentifiedAggregate(StubDomainEvent event) {
            apply(event);
        }

        @Override
        public boolean isLive() {
            return super.isLive();
        }

        @EventSourcingHandler
        public void myEventHandlerMethod(StubDomainEvent event, @Timestamp DateTime timestamp) {
            if (creationTime == null) {
                creationTime = timestamp;
            }
            aggregateIdentifier = "lateIdentifier";
            if (!event.nested) {
                apply(new StubDomainEvent(true));
            }
        }
    }

    private static class JavaxPersistenceIdIdentifiedAggregate extends AbstractAnnotatedAggregateRoot {

        @Id
        private String aggregateIdentifier;

        private JavaxPersistenceIdIdentifiedAggregate() {
            apply(new StubDomainEvent(false));
        }

        @EventSourcingHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            aggregateIdentifier = "lateIdentifier";
        }
    }

    private static class CustomParameterAggregateRoot extends SimpleAggregateRoot {

        private String secondParameter;

        @EventSourcingHandler
        public void myEventHandlerMethod(StubDomainEvent event, String secondParameter) {
            this.secondParameter = secondParameter;
            super.myEventHandlerMethod(event);
        }
    }

    private static class SimpleAggregateRoot extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private final UUID identifier;
        private int invocationCount;
        @EventSourcedMember
        private SimpleEntity entity;

        private SimpleAggregateRoot() {
            identifier = UUID.randomUUID();
            apply(new StubDomainEvent(false));
        }

        private SimpleAggregateRoot(UUID identifier) {
            this.identifier = identifier;
        }

        @EventSourcingHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
            if (entity == null) {
                entity = new SimpleEntity();
                apply(new StubDomainEvent(true));
            }
        }

        public SimpleEntity getEntity() {
            return entity;
        }

        public void doSomething() {
            apply(new StubDomainEvent(false));
        }
    }

    private static class SimpleEntity extends AbstractAnnotatedEntity {

        private int invocationCount;
        private List<StubDomainEvent> appliedEvents = new ArrayList<>();

        @EventSourcingHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
            appliedEvents.add(event);
        }
    }

    private static class StubDomainEvent implements Serializable {

        private static final long serialVersionUID = 834667054977749990L;

        private final boolean nested;

        private StubDomainEvent(boolean nested) {
            this.nested = nested;
        }

        @Override
        public String toString() {
            return "StubDomainEvent";
        }
    }
}
