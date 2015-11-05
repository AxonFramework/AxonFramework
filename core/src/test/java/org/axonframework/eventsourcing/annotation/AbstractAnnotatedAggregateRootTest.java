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
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.annotation.Timestamp;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.SimpleDomainEventStream;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.axonframework.testutils.RecordingEventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.persistence.Id;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractAnnotatedAggregateRootTest {

    private RecordingEventBus eventBus;
    private SimpleAggregateRoot testSubject;

    @Before
    public void setUp() {
        eventBus = new RecordingEventBus();
        UnitOfWork unitOfWork = DefaultUnitOfWork.startAndGet(null);
        unitOfWork.resources().put(EventBus.KEY, eventBus);
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testApplyEvent() {
        testSubject = new SimpleAggregateRoot();

        assertNotNull(testSubject.getIdentifier());
        // the first applied event applies another one
        assertEquals(2, eventBus.getPublishedEventCount());
        // this proves that a newly added entity is also notified of an event
        assertEquals(2, testSubject.getEntity().invocationCount);

        testSubject.doSomething();

        assertEquals(3, testSubject.invocationCount);
        assertEquals(3, testSubject.getEntity().invocationCount);

        // the nested handler must be invoked second
        assertFalse(testSubject.entity.appliedEvents.get(0).nested);
        assertTrue(testSubject.entity.appliedEvents.get(1).nested);
        assertFalse(testSubject.entity.appliedEvents.get(2).nested);

        for (int i = 0; i < eventBus.getPublishedEventCount(); i++) {
            assertSame(testSubject.entity.appliedEvents.get(i), eventBus.getPublishedEvents().get(i).getPayload());
        }
    }

    @Test
    public void testInitializeWithIdentifier() {
        testSubject = new SimpleAggregateRoot(UUID.randomUUID());
        assertEquals(0, eventBus.getPublishedEventCount());
    }

    //todo fix test
    @Ignore
    @Test
    public void testIdentifierInitialization_LateInitialization() {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate(new StubDomainEvent(false));
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", ((DomainEventMessage<?>) eventBus.getPublishedEvents().get(0))
                .getAggregateIdentifier());

        List<? extends EventMessage<?>> uncommittedEvents = eventBus.getPublishedEvents();
        assertFalse(((StubDomainEvent) uncommittedEvents.get(0).getPayload()).nested);
        assertTrue(((StubDomainEvent) uncommittedEvents.get(1).getPayload()).nested);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTimestampInReconstructionIsSameAsInitialTimestamp() {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate(new StubDomainEvent(false));
        Instant firstTimestamp = aggregate.creationTime;

        LateIdentifiedAggregate aggregate2 = new LateIdentifiedAggregate();
        aggregate2.initializeState(new SimpleDomainEventStream(
                (List<? extends DomainEventMessage<?>>) eventBus.getPublishedEvents()));

        assertSame(aggregate2.creationTime, firstTimestamp);
    }

    //todo fix test
    @Ignore
    @Test
    public void testIdentifierInitialization_JavaxPersistenceId() {
        JavaxPersistenceIdIdentifiedAggregate aggregate = new JavaxPersistenceIdIdentifiedAggregate();
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", ((DomainEventMessage<?>) eventBus.getPublishedEvents().get(0))
                .getAggregateIdentifier());
    }

    @Test
    public void testSerializationSetsLiveStateToTrue() throws Exception {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate(new StubDomainEvent(false));
        final XStreamSerializer serializer = new XStreamSerializer();
        SerializedObject<String> serialized = serializer.serialize(aggregate, String.class);

        LateIdentifiedAggregate deserializedAggregate = serializer.deserialize(serialized);
        assertTrue(deserializedAggregate.isLive());
    }

    //todo fix test
    @Ignore
    @Test
    public void testAggregateRetrievesParameterResolverFactoryFromUnitOfWork() {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet(null);
        uow.resources().put(ParameterResolverFactory.class.getName(), MultiParameterResolverFactory.ordered(
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

        assertEquals(0, eventBus.getPublishedEventCount());
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
            assertEquals(1, eventBus.getPublishedEventCount());
        }
        Field field = AbstractEventSourcedAggregateRoot.class.getDeclaredField("eventsToApply");
        assertEquals(0, ((Collection) ReflectionUtils.getFieldValue(field, testSubject)).size());
    }

    private static class LateIdentifiedAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String aggregateIdentifier;

        private Instant creationTime;

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
        public void myEventHandlerMethod(StubDomainEvent event, @Timestamp Instant timestamp) {
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
