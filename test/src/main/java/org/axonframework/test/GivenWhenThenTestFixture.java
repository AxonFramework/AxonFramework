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

package org.axonframework.test;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.monitoring.jmx.JmxConfiguration;
import org.axonframework.repository.AggregateNotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.axonframework.common.IdentifierValidator.validateIdentifier;

/**
 * A test fixture that allows the execution of given-when-then style test cases. For detailed usage information, see
 * {@link org.axonframework.test.FixtureConfiguration}.
 *
 * @author Allard Buijze
 * @since 0.6
 */
class GivenWhenThenTestFixture implements FixtureConfiguration, TestExecutor {

    private EventSourcingRepository<?> repository;
    private SimpleCommandBus commandBus;
    private EventBus eventBus;
    private Object aggregateIdentifier;
    private EventStore eventStore;

    private Collection<DomainEventMessage> givenEvents;

    private Deque<DomainEventMessage> storedEvents;
    private List<EventMessage> publishedEvents;
    private long sequenceNumber = 0;

    /**
     * Initializes a new given-when-then style test fixture.
     */
    GivenWhenThenTestFixture() {
        JmxConfiguration.getInstance().disableMonitoring();
        aggregateIdentifier = UUID.randomUUID().toString();
        eventBus = new RecordingEventBus();
        commandBus = new SimpleCommandBus();
        eventStore = new RecordingEventStore();
        clearGivenWhenState();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <T extends EventSourcedAggregateRoot> EventSourcingRepository<T> createRepository(Class<T> aggregateClass) {
        registerRepository(new EventSourcingRepository<T>(aggregateClass));
        return (EventSourcingRepository<T>) repository;
    }

    @Override
    public FixtureConfiguration registerRepository(EventSourcingRepository<?> eventSourcingRepository) {
        this.repository = eventSourcingRepository;
        eventSourcingRepository.setEventBus(eventBus);
        eventSourcingRepository.setEventStore(eventStore);
        return this;
    }

    @Override
    public FixtureConfiguration registerAnnotatedCommandHandler(Object annotatedCommandHandler) {
        AnnotationCommandHandlerAdapter.subscribe(annotatedCommandHandler, commandBus);
        return this;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public FixtureConfiguration registerCommandHandler(Class<?> commandType, CommandHandler commandHandler) {
        commandBus.subscribe(commandType, commandHandler);
        return this;
    }

    @Override
    public TestExecutor given(Object... domainEvents) {
        return given(Arrays.asList(domainEvents));
    }

    @Override
    public TestExecutor given(List<?> domainEvents) {
        clearGivenWhenState();
        for (Object event : domainEvents) {
            this.givenEvents.add(new GenericDomainEventMessage<Object>(
                    aggregateIdentifier,
                    sequenceNumber++,
                    event, null
            ));
        }
        return this;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public ResultValidator when(Object command) {
        ResultValidatorImpl resultValidator = new ResultValidatorImpl(storedEvents, publishedEvents);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(command), resultValidator);
        return resultValidator;
    }

    private void clearGivenWhenState() {
        storedEvents = new LinkedList<DomainEventMessage>();
        publishedEvents = new ArrayList<EventMessage>();
        givenEvents = new ArrayList<DomainEventMessage>();
        sequenceNumber = 0;
    }

    @Override
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public void setAggregateIdentifier(Object aggregateIdentifier) {
        validateIdentifier(aggregateIdentifier.getClass());
        this.aggregateIdentifier = aggregateIdentifier;
    }

    @Override
    public CommandBus getCommandBus() {
        return commandBus;
    }

    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public EventStore getEventStore() {
        return eventStore;
    }

    @Override
    public EventSourcingRepository<?> getRepository() {
        return repository;
    }

    private class RecordingEventStore implements EventStore {

        @Override
        public void appendEvents(String type, DomainEventStream events) {
            while (events.hasNext()) {
                DomainEventMessage next = events.next();
                validateIdentifier(next.getAggregateIdentifier().getClass());
                if (!storedEvents.isEmpty()) {
                    DomainEventMessage lastEvent = storedEvents.peekLast();
                    if (!lastEvent.getAggregateIdentifier().equals(next.getAggregateIdentifier())) {
                        throw new EventStoreException("Writing events for an unexpected aggregate. This could "
                                                              + "indicate that a wrong aggregate is being triggered.");
                    } else if (lastEvent.getSequenceNumber() != next.getSequenceNumber() - 1) {
                        throw new EventStoreException(String.format("Unexpected sequence number on stored event. "
                                                                            + "Expected %s, but got %s.",
                                                                    lastEvent.getSequenceNumber() + 1,
                                                                    next.getSequenceNumber()));
                    }
                }
                storedEvents.add(next);
            }
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier) {
            if (!aggregateIdentifier.equals(identifier)) {
                throw new EventStoreException("You probably want to use aggregateIdentifier() on your fixture "
                                                      + "to get the aggregate identifier to use");
            }
            if (givenEvents.isEmpty()) {
                throw new AggregateNotFoundException(identifier,
                                                     "No 'given' events were configured for this aggregate.");
            }
            return new SimpleDomainEventStream(givenEvents);
        }
    }

    private class RecordingEventBus implements EventBus {

        @Override
        public void publish(EventMessage event) {
            publishedEvents.add(event);
        }

        @Override
        public void subscribe(EventListener eventListener) {
        }

        @Override
        public void unsubscribe(EventListener eventListener) {
        }
    }
}
