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

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.AnnotatedSagaManager;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.utils.AutowiredResourceInjector;
import org.axonframework.test.utils.RecordingCommandBus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Fixture for testing Annotated Sagas based on events and time passing. This fixture allows resources to be configured
 * for the sagas to use.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class AnnotatedSagaTestFixture implements FixtureConfiguration, ContinuedGivenState {

    private final StubEventScheduler eventScheduler;
    private final AnnotatedSagaManager sagaManager;
    private final List<Object> registeredResources = new LinkedList<Object>();

    private Map<Object, AggregateEventPublisherImpl> aggregatePublishers = new HashMap<Object, AggregateEventPublisherImpl>();
    private FixtureExecutionResultImpl fixtureExecutionResult;

    /**
     * Creates an instance of the AnnotatedSagaTestFixture to test sagas of the given <code>sagaType</code>.
     *
     * @param sagaType The type of saga under test
     */
    @SuppressWarnings({"unchecked"})
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
        for (EventMessage event : eventScheduler.advanceTime(elapsedTime)) {
            sagaManager.handle(event);
        }
        return fixtureExecutionResult;
    }

    @Override
    public FixtureExecutionResult whenTimeAdvancesTo(DateTime newDateTime) {
        fixtureExecutionResult.startRecording();
        for (EventMessage event : eventScheduler.advanceTime(newDateTime)) {
            sagaManager.handle(event);
        }
        return fixtureExecutionResult;
    }

    @Override
    public void registerResource(Object resource) {
        registeredResources.add(resource);
    }

    @Override
    public GivenAggregateEventPublisher givenAggregate(Object aggregateIdentifier) {
        return getPublisherFor(aggregateIdentifier);
    }

    @Override
    public ContinuedGivenState givenAPublished(Object event) {
        sagaManager.handle(new GenericEventMessage<Object>(event));
        return this;
    }

    @Override
    public GivenAggregateEventPublisher andThenAggregate(Object aggregateIdentifier) {
        return givenAggregate(aggregateIdentifier);
    }

    @Override
    public ContinuedGivenState andThenAPublished(Object event) {
        sagaManager.handle(new GenericEventMessage<Object>(event));
        return this;
    }

    @Override
    public WhenAggregateEventPublisher whenAggregate(Object aggregateIdentifier) {
        fixtureExecutionResult.startRecording();
        return getPublisherFor(aggregateIdentifier);
    }

    @Override
    public FixtureExecutionResult whenPublishingA(Object event) {
        fixtureExecutionResult.startRecording();
        sagaManager.handle(new GenericEventMessage<Object>(event));
        return fixtureExecutionResult;
    }

    @Override
    public DateTime currentTime() {
        return eventScheduler.getCurrentDateTime();
    }

    private AggregateEventPublisherImpl getPublisherFor(Object aggregateIdentifier) {
        if (!aggregatePublishers.containsKey(aggregateIdentifier)) {
            aggregatePublishers.put(aggregateIdentifier, new AggregateEventPublisherImpl());
        }
        return aggregatePublishers.get(aggregateIdentifier);
    }

    private class AggregateEventPublisherImpl implements GivenAggregateEventPublisher, WhenAggregateEventPublisher {

        public AggregateEventPublisherImpl() {
        }

        @Override
        public ContinuedGivenState published(Object... events) {
            publish(events);
            return AnnotatedSagaTestFixture.this;
        }

        @Override
        public FixtureExecutionResult publishes(Object event) {
            publish(event);
            return fixtureExecutionResult;
        }

        private void publish(Object... events) {
            DateTimeUtils.setCurrentMillisFixed(currentTime().getMillis());
            try {
                for (Object event : events) {
                    sagaManager.handle(new GenericEventMessage<Object>(event));
                }
            } finally {
                DateTimeUtils.setCurrentMillisSystem();
            }
        }
    }
}
