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

package org.axonframework.test.saga;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.ApplicationEvent;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.AnnotatedSagaManager;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.utils.AutowiredResourceInjector;
import org.axonframework.test.utils.DomainEventUtils;
import org.axonframework.test.utils.RecordingCommandBus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaTestFixture implements FixtureConfiguration, ContinuedGivenState {

    private final StubEventScheduler eventScheduler;
    private final AnnotatedSagaManager sagaManager;
    private final List<Object> registeredResources = new LinkedList<Object>();

    private Map<AggregateIdentifier, AggregateEventPublisherImpl> aggregatePublishers = new HashMap<AggregateIdentifier, AggregateEventPublisherImpl>();
    private List<Event> publishedEvents = new ArrayList<Event>();
    private FixtureExecutionResultImpl fixtureExecutionResult;

    public AnnotatedSagaTestFixture(Class<? extends AbstractAnnotatedSaga> sagaType) {
        eventScheduler = new StubEventScheduler();
        GenericSagaFactory genericSagaFactory = new GenericSagaFactory();
        genericSagaFactory.setResourceInjector(new AutowiredResourceInjector(registeredResources));
        EventBus eventBus = new SimpleEventBus(false);
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        sagaManager = new AnnotatedSagaManager(sagaRepository, genericSagaFactory, eventBus, sagaType);
        sagaManager.setSuppressExceptions(false);

        registeredResources.add(eventBus);
        RecordingCommandBus commandBus = new RecordingCommandBus();
        registeredResources.add(commandBus);
        registeredResources.add(eventScheduler);
        fixtureExecutionResult = new FixtureExecutionResultImpl(sagaRepository, eventScheduler, eventBus, commandBus,
                                                                sagaType);
    }

    @Override
    public FixtureExecutionResult whenTimeElapses(Duration elapsedTime) {
        fixtureExecutionResult.startRecording();
        for (Event event : eventScheduler.advanceTime(elapsedTime)) {
            sagaManager.handle(event);
        }
        return fixtureExecutionResult;
    }

    @Override
    public FixtureExecutionResult whenTimeAdvancesTo(DateTime newDateTime) {
        fixtureExecutionResult.startRecording();
        for (Event event : eventScheduler.advanceTime(newDateTime)) {
            sagaManager.handle(event);
        }
        return fixtureExecutionResult;
    }

    @Override
    public void registerResource(Object resource) {
        registeredResources.add(resource);
    }

    @Override
    public GivenAggregateEventPublisher givenAggregate(AggregateIdentifier aggregateIdentifier) {
        return getPublisherFor(aggregateIdentifier);
    }

    @Override
    public ContinuedGivenState givenAPublished(ApplicationEvent applicationEvent) {
        sagaManager.handle(applicationEvent);
        return this;
    }

    @Override
    public GivenAggregateEventPublisher andThenAggregate(AggregateIdentifier aggregateIdentifier) {
        return givenAggregate(aggregateIdentifier);
    }

    @Override
    public ContinuedGivenState andThenAPublished(ApplicationEvent applicationEvent) {
        sagaManager.handle(applicationEvent);
        return this;
    }

    @Override
    public WhenAggregateEventPublisher whenAggregate(AggregateIdentifier aggregateIdentifier) {
        fixtureExecutionResult.startRecording();
        return getPublisherFor(aggregateIdentifier);
    }

    @Override
    public FixtureExecutionResult whenPublishingA(ApplicationEvent applicationEvent) {
        fixtureExecutionResult.startRecording();
        sagaManager.handle(applicationEvent);
        return fixtureExecutionResult;
    }

    private AggregateEventPublisherImpl getPublisherFor(AggregateIdentifier aggregateIdentifier) {
        if (!aggregatePublishers.containsKey(aggregateIdentifier)) {
            aggregatePublishers.put(aggregateIdentifier, new AggregateEventPublisherImpl(aggregateIdentifier));
        }
        return aggregatePublishers.get(aggregateIdentifier);
    }

    @Override
    public DateTime currentTime() {
        return eventScheduler.getCurrentDateTime();
    }

    private class AggregateEventPublisherImpl implements GivenAggregateEventPublisher, WhenAggregateEventPublisher {

        private final AggregateIdentifier aggregateIdentifier;
        private final AtomicLong sequenceCounter;

        public AggregateEventPublisherImpl(AggregateIdentifier aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
            sequenceCounter = new AtomicLong();
        }

        @Override
        public ContinuedGivenState published(DomainEvent... events) {
            publish(events);
            return AnnotatedSagaTestFixture.this;
        }

        @Override
        public FixtureExecutionResult publishes(DomainEvent event) {
            publish(event);
            return fixtureExecutionResult;
        }

        private void publish(DomainEvent... events) {
            DateTimeUtils.setCurrentMillisFixed(currentTime().getMillis());
            try {
                for (DomainEvent event : events) {
                    DomainEventUtils.setAggregateIdentifier(event, aggregateIdentifier);
                    DomainEventUtils.setSequenceNumber(event, sequenceCounter.getAndIncrement());
                    sagaManager.handle(event);
                }
            } finally {
                DateTimeUtils.setCurrentMillisSystem();
            }
        }
    }
}
