/*
 * Copyright (c) 2010-2016. Axon Framework
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
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.test.FixtureResourceParameterResolverFactory;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.IgnoreField;
import org.axonframework.test.utils.AutowiredResourceInjector;
import org.axonframework.test.utils.CallbackBehavior;
import org.axonframework.test.utils.RecordingCommandBus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Fixture for testing Annotated Sagas based on events and time passing. This fixture allows resources to be configured
 * for the sagas to use.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class AnnotatedSagaTestFixture<T> implements FixtureConfiguration, ContinuedGivenState {

    private final StubEventScheduler eventScheduler;
    private final AnnotatedSagaManager<T> sagaManager;
    private final List<Object> registeredResources = new LinkedList<>();

    private final Map<Object, AggregateEventPublisherImpl> aggregatePublishers = new HashMap<>();
    private final FixtureExecutionResultImpl<T> fixtureExecutionResult;
    private final RecordingCommandBus commandBus;
    private final MutableFieldFilter fieldFilters = new MutableFieldFilter();

    /**
     * Creates an instance of the AnnotatedSagaTestFixture to test sagas of the given <code>sagaType</code>.
     *
     * @param sagaType The type of saga under test
     */
    @SuppressWarnings({"unchecked"})
    public AnnotatedSagaTestFixture(Class<T> sagaType) {
        eventScheduler = new StubEventScheduler();
        EventBus eventBus = new SimpleEventBus();
        InMemorySagaStore sagaStore = new InMemorySagaStore();
        SagaRepository<T> sagaRepository = new AnnotatedSagaRepository<>(sagaType, sagaStore,
                                                                         new AutowiredResourceInjector(
                                                                                 registeredResources));
        sagaManager = new AnnotatedSagaManager<>(sagaType, sagaRepository, sagaType::newInstance, t -> true);
        sagaManager.setSuppressExceptions(false);

        registeredResources.add(eventBus);
        commandBus = new RecordingCommandBus();
        registeredResources.add(commandBus);
        registeredResources.add(eventScheduler);
        registeredResources.add(new DefaultCommandGateway(commandBus));
        fixtureExecutionResult = new FixtureExecutionResultImpl<>(sagaStore, eventScheduler, eventBus, commandBus,
                                                                  sagaType, fieldFilters);
        FixtureResourceParameterResolverFactory.clear();
        registeredResources.forEach(FixtureResourceParameterResolverFactory::registerResource);
    }

    @Override
    public FixtureExecutionResult whenTimeElapses(Duration elapsedTime) throws Exception {
        try {
            fixtureExecutionResult.startRecording();
            eventScheduler.advanceTime(elapsedTime, sagaManager::accept);
        } finally {
            FixtureResourceParameterResolverFactory.clear();
        }
        return fixtureExecutionResult;
    }

    @Override
    public FixtureExecutionResult whenTimeAdvancesTo(ZonedDateTime newDateTime) throws Exception {
        try {
            fixtureExecutionResult.startRecording();
            eventScheduler.advanceTime(newDateTime, sagaManager::accept);
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
    public GivenAggregateEventPublisher givenAggregate(String aggregateIdentifier) {
        return getPublisherFor(aggregateIdentifier);
    }

    @Override
    public ContinuedGivenState givenAPublished(Object event) throws Exception {
        sagaManager.accept(GenericEventMessage.asEventMessage(event));
        return this;
    }

    @Override
    public WhenState givenNoPriorActivity() {
        return this;
    }

    @Override
    public GivenAggregateEventPublisher andThenAggregate(String aggregateIdentifier) {
        return givenAggregate(aggregateIdentifier);
    }

    @Override
    public ContinuedGivenState andThenTimeElapses(final Duration elapsedTime) throws Exception {
        eventScheduler.advanceTime(elapsedTime, sagaManager::accept);
        return this;
    }

    @Override
    public ContinuedGivenState andThenTimeAdvancesTo(final ZonedDateTime newDateTime) throws Exception {
        eventScheduler.advanceTime(newDateTime, sagaManager::accept);
        return this;
    }

    @Override
    public ContinuedGivenState andThenAPublished(Object event) throws Exception {
        sagaManager.accept(GenericEventMessage.asEventMessage(event));
        return this;
    }

    @Override
    public WhenAggregateEventPublisher whenAggregate(String aggregateIdentifier) {
        fixtureExecutionResult.startRecording();
        return getPublisherFor(aggregateIdentifier);
    }

    @Override
    public FixtureExecutionResult whenPublishingA(Object event) throws Exception {
        try {
            fixtureExecutionResult.startRecording();
            sagaManager.accept(GenericEventMessage.asEventMessage(event));
        } finally {
            FixtureResourceParameterResolverFactory.clear();
        }

        return fixtureExecutionResult;
    }

    @Override
    public ZonedDateTime currentTime() {
        return eventScheduler.getCurrentDateTime();
    }

    @Override
    public <I> I registerCommandGateway(Class<I> gatewayInterface) {
        return registerCommandGateway(gatewayInterface, null);
    }

    @Override
    public <I> I registerCommandGateway(Class<I> gatewayInterface, final I stubImplementation) {
        GatewayProxyFactory factory = new StubAwareGatewayProxyFactory(stubImplementation,
                                                                       AnnotatedSagaTestFixture.this.commandBus);
        final I gateway = factory.createGateway(gatewayInterface);
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

    private AggregateEventPublisherImpl getPublisherFor(String aggregateIdentifier) {
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
            return new ReturnResultFromStub<>(delegate, stubImplementation);
        }

        @Override
        protected <R> InvocationHandler<R> wrapToReturnWithFixedTimeout(InvocationHandler<Future<R>> delegate,
                                                                        long timeout, TimeUnit timeUnit) {
            return new ReturnResultFromStub<>(delegate, stubImplementation);
        }

        @Override
        protected <R> InvocationHandler<R> wrapToReturnWithTimeoutInArguments(InvocationHandler<Future<R>> delegate,
                                                                              int timeoutIndex, int timeUnitIndex) {
            return new ReturnResultFromStub<>(delegate, stubImplementation);
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
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Exception {
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

        private final String aggregateIdentifier, type;
        private int sequenceNumber = 0;

        public AggregateEventPublisherImpl(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.type = "typeOf_" + aggregateIdentifier;
        }

        @Override
        public ContinuedGivenState published(Object... events) throws Exception {
            publish(events);
            return AnnotatedSagaTestFixture.this;
        }

        @Override
        public FixtureExecutionResult publishes(Object event) throws Exception {
            try {
                publish(event);
            } finally {
                FixtureResourceParameterResolverFactory.clear();
            }
            return fixtureExecutionResult;
        }

        private void publish(Object... events) throws Exception {
            try {
                Clock.fixed(currentTime().toInstant(), currentTime().getZone());
                for (Object event : events) {
                    if (event instanceof EventMessage<?>) {
                        EventMessage<?> eventMessage = (EventMessage<?>) event;
                        sagaManager.accept(new GenericDomainEventMessage<>(type, aggregateIdentifier,
                                                                           sequenceNumber++, eventMessage.getPayload(),
                                                                           eventMessage.getMetaData(),
                                                                           eventMessage.getIdentifier(),
                                                                           eventMessage.getTimestamp()));
                    } else {
                        sagaManager.accept(new GenericDomainEventMessage<>(type, aggregateIdentifier,
                                                                           sequenceNumber++, event));
                    }
                }
            } finally {
                Clock.systemDefaultZone();
            }
        }
    }

    private class MutableFieldFilter implements FieldFilter {

        private final List<FieldFilter> filters = new ArrayList<>();

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
