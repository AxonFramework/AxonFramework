/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.unitofwork;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.junit.*;
import org.mockito.*;

import static org.mockito.Mockito.*;

/**
 * Test cases that verify proper behavior in nesting of Unit of Work.
 *
 * @author Patrick Haas
 * @author Allard Buijze
 */
public class NestableUnitOfWorkTest {

    private UnitOfWorkFactory uowFactory;
    private EventBus eventBus;

    @Before
    public void setUp() throws Exception {
        eventBus = new SimpleEventBus();
        uowFactory = new DefaultUnitOfWorkFactory();
    }

    @Test
    public void testPublishEvent() {
        final EventMessage initialEvent = GenericEventMessage.asEventMessage(new Object());
        final EventMessage secondEvent = GenericEventMessage.asEventMessage(new Object());
        final EventMessage finalEvent = GenericEventMessage.asEventMessage(new Object());

        final EventListener eventListener = spy(new EventListener() {
            @Override
            public void handle(EventMessage event) {
                UnitOfWork inner = uowFactory.createUnitOfWork();

                if (event == initialEvent) {
                    // The initial event registers an aggregate in a nested Unit of Work
                    inner.publishEvent(secondEvent, eventBus);
                    Aggregate aggregate = new Aggregate("123");
                    inner.registerAggregate(aggregate, eventBus, new SaveAggregateCallback<Aggregate>() {
                        @Override
                        public void save(Aggregate aggregate) {
                        }
                    });
                } else if (event.getPayloadType() == AggregateCreatedEvent.class) {
                    // The AggregateCreatedEvent publishes another event.
                    inner.publishEvent(finalEvent,
                                       eventBus); // XXX inner delegates to outer UoW, which is already done publishing
                }
                inner.commit();
            }
        });
        eventBus.subscribe(eventListener);

        UnitOfWork outer = uowFactory.createUnitOfWork();
        outer.publishEvent(initialEvent, eventBus);
        outer.commit(); // kick off the chain of events

        InOrder inOrder = inOrder(eventListener);
        inOrder.verify(eventListener).handle(initialEvent);
        inOrder.verify(eventListener).handle(secondEvent);
        inOrder.verify(eventListener).handle(isA(DomainEventMessage.class));
        inOrder.verify(eventListener).handle(finalEvent);
    }

    @Test
    public void testEventNotPublishedOnUnitOfWorkRollback() {
        final EventMessage initialEvent = GenericEventMessage.asEventMessage(new Object());
        final EventMessage secondEvent = GenericEventMessage.asEventMessage(new Object());
        final EventMessage finalEvent = GenericEventMessage.asEventMessage(new Object());

        final EventListener eventListener = spy(new EventListener() {
            @Override
            public void handle(EventMessage event) {
                UnitOfWork inner = uowFactory.createUnitOfWork();

                if (event == initialEvent) {
                    // The initial event registers an aggregate in a nested Unit of Work
                    inner.publishEvent(secondEvent, eventBus);
                    Aggregate aggregate = new Aggregate("123");
                    inner.registerAggregate(aggregate, eventBus, new SaveAggregateCallback<Aggregate>() {
                        @Override
                        public void save(Aggregate aggregate) {
                        }
                    });
                } else if (event.getPayloadType() == AggregateCreatedEvent.class) {
                    // The AggregateCreatedEvent publishes another event.
                    inner.publishEvent(finalEvent,
                                       eventBus); // XXX inner delegates to outer UoW, which is already done publishing
                    inner.rollback();
                }
                if (inner.isStarted()) {
                    inner.commit();
                }
            }
        });
        eventBus.subscribe(eventListener);

        UnitOfWork outer = uowFactory.createUnitOfWork();
        outer.publishEvent(initialEvent, eventBus);
        outer.commit(); // kick off the chain of events

        InOrder inOrder = inOrder(eventListener);
        inOrder.verify(eventListener).handle(initialEvent);
        inOrder.verify(eventListener).handle(secondEvent);
        inOrder.verify(eventListener).handle(isA(DomainEventMessage.class));
        verify(eventListener, never()).handle(finalEvent);
    }

    @Test
    public void testPublishEvent_RegistrationListenerOnlyInvokedOncePerEvent() {
        final EventMessage initialEvent = GenericEventMessage.asEventMessage(new Object());
        final EventMessage secondEvent = GenericEventMessage.asEventMessage(new Object());
        final EventMessage finalEvent = GenericEventMessage.asEventMessage(new Object());
        final UnitOfWorkListener mockUoWListener = spy(new UnitOfWorkListenerAdapter() {
        });

        final EventListener eventListener = spy(new EventListener() {
            @Override
            public void handle(EventMessage event) {
                UnitOfWork inner = uowFactory.createUnitOfWork();
                inner.registerListener(mockUoWListener);

                if (event == initialEvent) {
                    // The initial event registers an aggregate in a nested Unit of Work
                    inner.publishEvent(secondEvent, eventBus);
                    Aggregate aggregate = new Aggregate("123");
                    inner.registerAggregate(aggregate, eventBus, new SaveAggregateCallback<Aggregate>() {
                        @Override
                        public void save(Aggregate aggregate) {
                        }
                    });
                } else if (event.getPayloadType() == AggregateCreatedEvent.class) {
                    // The AggregateCreatedEvent publishes another event.
                    inner.publishEvent(finalEvent,
                                       eventBus); // XXX inner delegates to outer UoW, which is already done publishing
                }
                inner.commit();
            }
        });
        eventBus.subscribe(eventListener);

        UnitOfWork outer = uowFactory.createUnitOfWork();
        outer.registerListener(mockUoWListener);
        outer.publishEvent(initialEvent, eventBus);
        outer.commit(); // kick off the chain of events

        InOrder inOrder = inOrder(eventListener, mockUoWListener);
        inOrder.verify(mockUoWListener).onEventRegistered(outer, initialEvent);
        inOrder.verify(eventListener).handle(initialEvent);

        inOrder.verify(mockUoWListener).onEventRegistered(any(UnitOfWork.class), eq(secondEvent));
        inOrder.verify(mockUoWListener).onEventRegistered(any(UnitOfWork.class), isA(DomainEventMessage.class));
        inOrder.verify(eventListener).handle(secondEvent);
        inOrder.verify(eventListener).handle(isA(DomainEventMessage.class));
        inOrder.verify(mockUoWListener).onEventRegistered(any(UnitOfWork.class), eq(finalEvent));
        inOrder.verify(eventListener).handle(finalEvent);

        verify(mockUoWListener, never()).onEventRegistered(outer, secondEvent);
        verify(mockUoWListener, never()).onEventRegistered(outer, finalEvent);
        verify(mockUoWListener, never()).onEventRegistered(eq(outer), isA(DomainEventMessage.class));
    }

	/*
     * Domain Model
	 */

    public static class AggregateCreatedEvent {

        @AggregateIdentifier
        final String id;

        public AggregateCreatedEvent(String id) {
            this.id = id;
        }
    }

    @SuppressWarnings("serial")
    public static class Aggregate extends AbstractAnnotatedAggregateRoot<String> {

        @AggregateIdentifier
        public String id;

        @SuppressWarnings("unused")
        private Aggregate() {
        }

        public Aggregate(String id) {
            apply(new AggregateCreatedEvent(id));
        }

        @EventHandler
        private void created(AggregateCreatedEvent event) {
            this.id = event.id;
        }
    }
}
