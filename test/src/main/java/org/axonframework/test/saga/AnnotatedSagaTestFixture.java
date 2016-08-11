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

package org.axonframework.test.saga;

import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.gateway.GatewayProxyFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventTemplate;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.AnnotatedSagaManager;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.axonframework.test.FixtureResourceParameterResolverFactory;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.IgnoreField;
import org.axonframework.test.utils.AutowiredResourceInjector;
import org.axonframework.test.utils.CallbackBehavior;
import org.axonframework.test.utils.RecordingCommandBus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    private final Map<Object, AggregateEventPublisherImpl> aggregatePublishers = new HashMap<Object, AggregateEventPublisherImpl>();
    private final FixtureExecutionResultImpl fixtureExecutionResult;
    private final RecordingCommandBus commandBus;
    private final MutableFieldFilter fieldFilters = new MutableFieldFilter();

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
        EventBus eventBus = new SimpleEventBus();
        InMemorySagaRepository sagaRepository = new InMemorySagaRepository();
        sagaManager = new AnnotatedSagaManager(sagaRepository, genericSagaFactory, sagaType);
        sagaManager.setSuppressExceptions(false);

        registeredResources.add(eventBus);
        registeredResources.add(new EventTemplate(eventBus));
        commandBus = new RecordingCommandBus();
        registeredResources.add(commandBus);
        registeredResources.add(eventScheduler);
        registeredResources.add(new DefaultCommandGateway(commandBus));
        fixtureExecutionResult = new FixtureExecutionResultImpl(sagaRepository, eventScheduler, eventBus, commandBus,
                                                                sagaType, fieldFilters);
        FixtureResourceParameterResolverFactory.clear();
        for (Object resource : registeredResources) {
            FixtureResourceParameterResolverFactory.registerResource(resource);
        }
    }

    @Override
    public FixtureExecutionResult whenTimeElapses(Duration elapsedTime) {
        try {
            fixtureExecutionResult.startRecording();
            for (EventMessage event : eventScheduler.advanceTime(elapsedTime)) {
                sagaManager.handle(event);
            }
        } finally {
            FixtureResourceParameterResolverFactory.clear();
        }
        return fixtureExecutionResult;
    }

    @Override
    public FixtureExecutionResult whenTimeAdvancesTo(DateTime newDateTime) {
        try {
            fixtureExecutionResult.startRecording();
            for (EventMessage event : eventScheduler.advanceTime(newDateTime)) {
                sagaManager.handle(event);
            }
        } finally {
            FixtureResourceParameterResolverFactory.clear();
        }

        return fixtureExecutionResult;
    }

    @Override
    public void registerResource(Object resource) {
        registeredResources.add(resource);
        FixtureResourceParameterResolverFactory.registerResource(resource);
    }

    @Override
    public void setCallbackBehavior(CallbackBehavior callbackBehavior) {
        commandBus.setCallbackBehavior(callbackBehavior);
    }

    @Override
    public GivenAggregateEventPublisher givenAggregate(Object aggregateIdentifier) {
        return getPublisherFor(aggregateIdentifier);
    }

    @Override
    public ContinuedGivenState givenAPublished(Object event) {
        sagaManager.handle(timeCorrectedEventMessage(event));
        return this;
    }

    @Override
    public WhenState givenNoPriorActivity() {
        return this;
    }

    @Override
    public GivenAggregateEventPublisher andThenAggregate(Object aggregateIdentifier) {
        return givenAggregate(aggregateIdentifier);
    }

    @Override
    public ContinuedGivenState andThenTimeElapses(final Duration elapsedTime) {
        for (EventMessage event : eventScheduler.advanceTime(elapsedTime)) {
            sagaManager.handle(event);
        }
        return this;
    }

    @Override
    public ContinuedGivenState andThenTimeAdvancesTo(final DateTime newDateTime) {
        for (EventMessage event : eventScheduler.advanceTime(newDateTime)) {
            sagaManager.handle(event);
        }
        return this;
    }

    @Override
    public ContinuedGivenState andThenAPublished(Object event) {
        sagaManager.handle(timeCorrectedEventMessage(event));
        return this;
    }

    @Override
    public WhenAggregateEventPublisher whenAggregate(Object aggregateIdentifier) {
        fixtureExecutionResult.startRecording();
        return getPublisherFor(aggregateIdentifier);
    }

    @Override
    public FixtureExecutionResult whenPublishingA(Object event) {
        try {
            DateTimeUtils.setCurrentMillisProvider(eventScheduler);
            fixtureExecutionResult.startRecording();
            sagaManager.handle(timeCorrectedEventMessage(event));
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
            FixtureResourceParameterResolverFactory.clear();
        }

        return fixtureExecutionResult;
    }

    private EventMessage<Object> timeCorrectedEventMessage(Object event) {
        if (event instanceof EventMessage) {
            EventMessage<?> msg = (EventMessage<?>) event;
            return new GenericEventMessage<Object>(msg.getIdentifier(), currentTime(),
                                                   msg.getPayload(), msg.getMetaData());
        } else {
            return timeCorrectedEventMessage(GenericEventMessage.asEventMessage(event));
        }
    }

    @Override
    public DateTime currentTime() {
        return eventScheduler.getCurrentDateTime();
    }

    @Override
    public <T> T registerCommandGateway(Class<T> gatewayInterface) {
        return registerCommandGateway(gatewayInterface, null);
    }

    @Override
    public <T> T registerCommandGateway(Class<T> gatewayInterface, final T stubImplementation) {
        GatewayProxyFactory factory = new StubAwareGatewayProxyFactory(stubImplementation,
                                                                       AnnotatedSagaTestFixture.this.commandBus);
        final T gateway = factory.createGateway(gatewayInterface);
        registerResource(gateway);
        return gateway;
    }

    @Override
    public FixtureConfiguration registerFieldFilter(FieldFilter fieldFilter) {
        this.fieldFilters.add(fieldFilter);
        return this;
    }

    @Override
    public FixtureConfiguration registerIgnoredField(Class<?> declaringClass, String fieldName) {
        return registerFieldFilter(new IgnoreField(declaringClass, fieldName));
    }

    private AggregateEventPublisherImpl getPublisherFor(Object aggregateIdentifier) {
        if (!aggregatePublishers.containsKey(aggregateIdentifier)) {
            aggregatePublishers.put(aggregateIdentifier, new AggregateEventPublisherImpl(aggregateIdentifier));
        }
        return aggregatePublishers.get(aggregateIdentifier);
    }

    /**
     * GatewayProxyFactory that is aware of a stub implementation that defines the behavior for the callback.
     */
    private static class StubAwareGatewayProxyFactory extends GatewayProxyFactory {

        private final Object stubImplementation;

        public StubAwareGatewayProxyFactory(Object stubImplementation, RecordingCommandBus commandBus) {
            super(commandBus);
            this.stubImplementation = stubImplementation;
        }

        @Override
        protected <R> InvocationHandler<R> wrapToWaitForResult(final InvocationHandler<Future<R>> delegate) {
            return new ReturnResultFromStub<R>(delegate, stubImplementation);
        }

        @Override
        protected <R> InvocationHandler<R> wrapToReturnWithFixedTimeout(
                InvocationHandler<Future<R>> delegate, long timeout, TimeUnit timeUnit) {
            return new ReturnResultFromStub<R>(delegate, stubImplementation);
        }

        @Override
        protected <R> InvocationHandler<R> wrapToReturnWithTimeoutInArguments(
                InvocationHandler<Future<R>> delegate, int timeoutIndex, int timeUnitIndex) {
            return new ReturnResultFromStub<R>(delegate, stubImplementation);
        }
    }

    /**
     * Invocation handler that uses a stub implementation (of not <code>null</code>) to define the value to return from
     * a handler invocation. If none is provided, the returned future is checked for a value. If that future is not
     * "done" (for example because no callback behavior was provided), it returns <code>null</code>.
     *
     * @param <R> The return type of the method invocation
     */
    private static class ReturnResultFromStub<R> implements GatewayProxyFactory.InvocationHandler<R> {

        private final GatewayProxyFactory.InvocationHandler<Future<R>> dispatcher;
        private final Object stubGateway;

        public ReturnResultFromStub(GatewayProxyFactory.InvocationHandler<Future<R>> dispatcher, Object stubGateway) {
            this.dispatcher = dispatcher;
            this.stubGateway = stubGateway;
        }

        @SuppressWarnings("unchecked")
        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Throwable {
            Future<R> future = dispatcher.invoke(proxy, invokedMethod, args);
            if (stubGateway != null) {
                return (R) invokedMethod.invoke(stubGateway, args);
            }
            if (future.isDone()) {
                return future.get();
            }
            return null;
        }
    }

    private class AggregateEventPublisherImpl implements GivenAggregateEventPublisher, WhenAggregateEventPublisher {

        private final Object aggregateIdentifier;
        private int sequenceNumber = 0;

        public AggregateEventPublisherImpl(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        @Override
        public ContinuedGivenState published(Object... events) {
            publish(events);
            return AnnotatedSagaTestFixture.this;
        }

        @Override
        public FixtureExecutionResult publishes(Object event) {
            try {
                publish(event);
            } finally {
                FixtureResourceParameterResolverFactory.clear();
            }
            return fixtureExecutionResult;
        }

        private void publish(Object... events) {
            for (Object event : events) {
                sagaManager.handle(timeCorrectedDomainEventMessage(event));
            }
        }

        private GenericDomainEventMessage<Object> timeCorrectedDomainEventMessage(Object event) {
            EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(event);
            return new GenericDomainEventMessage<Object>(eventMessage.getIdentifier(),
                                                         currentTime(),
                                                         aggregateIdentifier,
                                                         sequenceNumber++,
                                                         eventMessage.getPayload(),
                                                         eventMessage.getMetaData());
        }
    }

    private class MutableFieldFilter implements FieldFilter {

        private final List<FieldFilter> filters = new ArrayList<FieldFilter>();

        @Override
        public boolean accept(Field field) {
            for (FieldFilter filter : filters) {
                if (!filter.accept(field)) {
                    return false;
                }
            }
            return true;
        }

        public void add(FieldFilter fieldFilter) {
            filters.add(fieldFilter);
        }
    }
}
