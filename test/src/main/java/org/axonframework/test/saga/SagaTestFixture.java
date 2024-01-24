/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.saga;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGatewayFactory;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathHandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.saga.AnnotatedSagaManager;
import org.axonframework.modelling.saga.ResourceInjector;
import org.axonframework.modelling.saga.SagaRepository;
import org.axonframework.modelling.saga.SimpleResourceInjector;
import org.axonframework.modelling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.test.FixtureExecutionException;
import org.axonframework.test.deadline.StubDeadlineManager;
import org.axonframework.test.eventscheduler.StubEventScheduler;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.IgnoreField;
import org.axonframework.test.utils.CallbackBehavior;
import org.axonframework.test.utils.RecordingCommandBus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.fieldsOf;

/**
 * Fixture for testing Annotated Sagas based on events and time passing. This fixture allows resources to be configured
 * for the sagas to use.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class SagaTestFixture<T> implements FixtureConfiguration, ContinuedGivenState {

    private final RecordingCommandBus commandBus;
    private final EventBus eventBus;
    private final StubEventScheduler eventScheduler;
    private final StubDeadlineManager deadlineManager;
    private final LinkedList<ParameterResolverFactory> registeredParameterResolverFactories = new LinkedList<>();
    private final LinkedList<HandlerDefinition> registeredHandlerDefinitions = new LinkedList<>();
    private final LinkedList<HandlerEnhancerDefinition> registeredHandlerEnhancerDefinitions = new LinkedList<>();
    private final LinkedList<MessageHandlerInterceptor<? super EventMessage<?>>> eventHandlerInterceptors = new LinkedList<>();
    private final RecordingListenerInvocationErrorHandler recordingListenerInvocationErrorHandler;

    private final Class<T> sagaType;
    private final InMemorySagaStore sagaStore;
    private AnnotatedSagaManager<T> sagaManager;
    private final LinkedList<Object> registeredResources = new LinkedList<>();
    private ResourceInjector resourceInjector;

    private final FixtureExecutionResultImpl<T> fixtureExecutionResult;
    private final Map<Object, AggregateEventPublisherImpl> aggregatePublishers = new HashMap<>();
    private final MutableFieldFilter fieldFilters = new MutableFieldFilter();

    private boolean transienceCheckEnabled = true;
    private boolean resourcesInitialized = false;
    private final AtomicLong globalSequence = new AtomicLong();

    /**
     * Creates an instance of the AnnotatedSagaTestFixture to test sagas of the given {@code sagaType}.
     *
     * @param sagaType The type of saga under test
     */
    public SagaTestFixture(Class<T> sagaType) {
        commandBus = new RecordingCommandBus();
        eventBus = SimpleEventBus.builder().build();
        eventScheduler = new StubEventScheduler();
        deadlineManager = new StubDeadlineManager();
        registeredParameterResolverFactories.add(new SimpleResourceParameterResolverFactory(registeredResources));
        registeredParameterResolverFactories.add(ClasspathParameterResolverFactory.forClass(sagaType));
        registeredHandlerDefinitions.add(ClasspathHandlerDefinition.forClass(sagaType));
        registeredHandlerEnhancerDefinitions.add(ClasspathHandlerEnhancerDefinition.forClass(sagaType));
        recordingListenerInvocationErrorHandler = new RecordingListenerInvocationErrorHandler(new LoggingErrorHandler());

        this.sagaType = sagaType;
        sagaStore = new InMemorySagaStore();

        registeredResources.add(eventBus);
        registeredResources.add(commandBus);
        registeredResources.add(eventScheduler);
        registeredResources.add(deadlineManager);
        registeredResources.add(DefaultCommandGateway.builder().commandBus(commandBus).build());

        fixtureExecutionResult = new FixtureExecutionResultImpl<>(
                sagaStore, eventScheduler, deadlineManager, eventBus, commandBus, sagaType, fieldFilters, recordingListenerInvocationErrorHandler);
    }

    /**
     * Handles the given {@code event} in the scope of a Unit of Work. If handling the event results in an exception the
     * exception will be wrapped in a {@link FixtureExecutionException}.
     *
     * @param event The event message to handle
     */
    protected void handleInSaga(EventMessage<?> event) {
        ensureSagaResourcesInitialized();
        TrackedEventMessage<?> trackedEventMessage = asTrackedEventMessage(event);
        DefaultUnitOfWork<? extends EventMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(trackedEventMessage);
        ResultMessage<?> resultMessage = unitOfWork.executeWithResult(() -> new DefaultInterceptorChain<>(
                unitOfWork,
                eventHandlerInterceptors,
                (MessageHandler<EventMessage<?>>) message -> {
                    sagaManager.handle(message, null, Segment.ROOT_SEGMENT);
                    return null;
                }).proceedSync()
        );

        if (resultMessage.isExceptional()) {
            Throwable e = resultMessage.exceptionResult();
            if (Error.class.isAssignableFrom(e.getClass())) {
                throw (Error) e;
            }
            throw new FixtureExecutionException("Exception occurred while handling an event", e);
        }
    }

    private TrackedEventMessage<?> asTrackedEventMessage(EventMessage<?> event) {
        return new GenericTrackedEventMessage<>(
                new GlobalSequenceTrackingToken(globalSequence.getAndIncrement()), event
        );
    }

    /**
     * Handles the given {@code deadlineMessage} in the saga described by the given {@code sagaDescriptor}. Deadline
     * message is handled in the scope of a {@link org.axonframework.messaging.unitofwork.UnitOfWork}. If handling the
     * deadline results in an exception, the exception will be wrapped in a {@link FixtureExecutionException}.
     *
     * @param sagaDescriptor  A {@link ScopeDescriptor} describing the saga under test
     * @param deadlineMessage The {@link DeadlineMessage} to be handled
     */
    protected void handleDeadline(ScopeDescriptor sagaDescriptor, DeadlineMessage<?> deadlineMessage) throws Exception {
        ensureSagaResourcesInitialized();
        sagaManager.send(deadlineMessage, sagaDescriptor);
    }

    /**
     * Initializes the saga resources if it hasn't already done so. If once initialized, this method does nothing.
     */
    protected void ensureSagaResourcesInitialized() {
        if (!resourcesInitialized) {
            SagaRepository<T> sagaRepository = AnnotatedSagaRepository.<T>builder()
                    .sagaType(sagaType)
                    .parameterResolverFactory(getParameterResolverFactory())
                    .handlerDefinition(getHandlerDefinition())
                    .sagaStore(sagaStore)
                    .resourceInjector(getResourceInjector())
                    .build();
            sagaManager = AnnotatedSagaManager.<T>builder()
                    .sagaRepository(sagaRepository)
                    .sagaType(sagaType)
                    .parameterResolverFactory(getParameterResolverFactory())
                    .handlerDefinition(getHandlerDefinition())
                    .listenerInvocationErrorHandler(recordingListenerInvocationErrorHandler)
                    .build();
            resourcesInitialized = true;
        }
    }

    private ParameterResolverFactory getParameterResolverFactory() {
        return MultiParameterResolverFactory.ordered(registeredParameterResolverFactories);
    }

    private HandlerDefinition getHandlerDefinition() {
        HandlerEnhancerDefinition handlerEnhancerDefinition =
                MultiHandlerEnhancerDefinition.ordered(registeredHandlerEnhancerDefinitions);
        return MultiHandlerDefinition.ordered(registeredHandlerDefinitions, handlerEnhancerDefinition);
    }

    private ResourceInjector getResourceInjector() {
        TransienceValidatingResourceInjector defaultResourceInjector =
                new TransienceValidatingResourceInjector(registeredResources, transienceCheckEnabled);
        return resourceInjector != null
                ? new WrappingResourceInjector(resourceInjector, defaultResourceInjector)
                : defaultResourceInjector;
    }

    @Override
    public FixtureConfiguration withTransienceCheckDisabled() {
        this.transienceCheckEnabled = false;
        return this;
    }

    @Override
    public FixtureExecutionResult whenTimeElapses(Duration elapsedTime) {
        try {
            fixtureExecutionResult.startRecording();
            eventScheduler.advanceTimeBy(elapsedTime, this::handleInSaga);
            deadlineManager.advanceTimeBy(elapsedTime, this::handleDeadline);
        } catch (Exception e) {
            throw new FixtureExecutionException("Exception occurred while trying to advance time " +
                                                        "and handle scheduled events", e);
        }
        return fixtureExecutionResult;
    }

    @Override
    public FixtureExecutionResult whenTimeAdvancesTo(Instant newDateTime) {
        try {
            fixtureExecutionResult.startRecording();
            eventScheduler.advanceTimeTo(newDateTime, this::handleInSaga);
            deadlineManager.advanceTimeTo(newDateTime, this::handleDeadline);
        } catch (Exception e) {
            throw new FixtureExecutionException("Exception occurred while trying to advance time " +
                                                        "and handle scheduled events", e);
        }

        return fixtureExecutionResult;
    }

    @Override
    public void registerResource(Object resource) {
        registeredResources.addFirst(resource);
    }

    @Override
    public FixtureConfiguration registerParameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
        this.registeredParameterResolverFactories.addFirst(parameterResolverFactory);
        return this;
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
    public ContinuedGivenState givenAPublished(Object event) {
        handleInSaga(timeCorrectedEventMessage(event));
        return this;
    }

    @Override
    public ContinuedGivenState givenCurrentTime(Instant currentTime) {
        eventScheduler.initializeAt(currentTime);
        deadlineManager.initializeAt(currentTime);
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
    public ContinuedGivenState andThenTimeElapses(final Duration elapsedTime) {
        eventScheduler.advanceTimeBy(elapsedTime, this::handleInSaga);
        deadlineManager.advanceTimeBy(elapsedTime, this::handleDeadline);
        return this;
    }

    @Override
    public ContinuedGivenState andThenTimeAdvancesTo(final Instant newDateTime) {
        eventScheduler.advanceTimeTo(newDateTime, this::handleInSaga);
        deadlineManager.advanceTimeTo(newDateTime, this::handleDeadline);
        return this;
    }

    @Override
    public ContinuedGivenState andThenAPublished(Object event) {
        handleInSaga(timeCorrectedEventMessage(event));
        return this;
    }

    @Override
    public ContinuedGivenState andThenAPublished(Object event, Map<String, ?> metaData) {
        EventMessage<?> msg = GenericEventMessage.asEventMessage(event).andMetaData(metaData);

        handleInSaga(timeCorrectedEventMessage(msg));
        return this;
    }

    @Override
    public WhenAggregateEventPublisher whenAggregate(String aggregateIdentifier) {
        fixtureExecutionResult.startRecording();
        return getPublisherFor(aggregateIdentifier);
    }

    @Override
    public FixtureExecutionResult whenPublishingA(Object event) {
        fixtureExecutionResult.startRecording();
        handleInSaga(timeCorrectedEventMessage(event));
        return fixtureExecutionResult;
    }

    @Override
    public FixtureExecutionResult whenPublishingA(Object event, Map<String, ?> metaData) {
        EventMessage<?> msg = GenericEventMessage.asEventMessage(event).andMetaData(metaData);

        fixtureExecutionResult.startRecording();
        handleInSaga(timeCorrectedEventMessage(msg));
        return fixtureExecutionResult;
    }

    private EventMessage<Object> timeCorrectedEventMessage(Object event) {
        EventMessage<?> msg = GenericEventMessage.asEventMessage(event);
        return new GenericEventMessage<>(msg.getIdentifier(), msg.getPayload(), msg.getMetaData(), currentTime());
    }

    @Override
    public Instant currentTime() {
        return eventScheduler.getCurrentDateTime();
    }

    @Override
    public <I> I registerCommandGateway(Class<I> gatewayInterface) {
        return registerCommandGateway(gatewayInterface, null);
    }

    @Override
    public <I> I registerCommandGateway(Class<I> gatewayInterface, final I stubImplementation) {
        CommandGatewayFactory factory = StubAwareCommandGatewayFactory.builder()
                                                                      .commandBus(SagaTestFixture.this.commandBus)
                                                                      .stubImplementation(stubImplementation)
                                                                      .build();
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

    @Override
    public FixtureConfiguration registerHandlerDefinition(HandlerDefinition handlerDefinition) {
        this.registeredHandlerDefinitions.addFirst(handlerDefinition);
        return this;
    }

    @Override
    public FixtureConfiguration registerHandlerEnhancerDefinition(HandlerEnhancerDefinition handlerEnhancerDefinition) {
        this.registeredHandlerEnhancerDefinitions.addFirst(handlerEnhancerDefinition);
        return this;
    }

    @Override
    public FixtureConfiguration registerDeadlineDispatchInterceptor(
            MessageDispatchInterceptor<? super DeadlineMessage<?>> deadlineDispatchInterceptor
    ) {
        this.deadlineManager.registerDispatchInterceptor(deadlineDispatchInterceptor);
        return this;
    }

    @Override
    public FixtureConfiguration registerDeadlineHandlerInterceptor(
            MessageHandlerInterceptor<? super DeadlineMessage<?>> deadlineHandlerInterceptor
    ) {
        this.deadlineManager.registerHandlerInterceptor(deadlineHandlerInterceptor);
        return this;
    }

    @Override
    public FixtureConfiguration registerEventHandlerInterceptor(
            MessageHandlerInterceptor<? super EventMessage<?>> eventHandlerInterceptor
    ) {
        this.eventHandlerInterceptors.add(eventHandlerInterceptor);
        return this;
    }

    @Override
    public FixtureConfiguration registerStartRecordingCallback(Runnable onStartRecordingCallback) {
        this.fixtureExecutionResult.registerStartRecordingCallback(onStartRecordingCallback);
        return this;
    }

    @Override
    public FixtureConfiguration registerListenerInvocationErrorHandler(
            ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
        recordingListenerInvocationErrorHandler.setListenerInvocationErrorHandler(listenerInvocationErrorHandler);
        return this;
    }

    @Override
    public FixtureConfiguration suppressExceptionInGivenPhase(boolean suppress) {
        recordingListenerInvocationErrorHandler.failOnErrorInPreparation(!suppress);
        return this;
    }

    @Override
    public FixtureConfiguration registerResourceInjector(ResourceInjector resourceInjector) {
        this.resourceInjector = resourceInjector;
        return this;
    }

    private AggregateEventPublisherImpl getPublisherFor(String aggregateIdentifier) {
        if (!aggregatePublishers.containsKey(aggregateIdentifier)) {
            aggregatePublishers.put(aggregateIdentifier, new AggregateEventPublisherImpl(aggregateIdentifier));
        }
        return aggregatePublishers.get(aggregateIdentifier);
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public RecordingCommandBus getCommandBus() {
        return commandBus;
    }

    /**
     * CommandGatewayFactory that is aware of a stub implementation that defines the behavior for the callback.
     */
    private static class StubAwareCommandGatewayFactory extends CommandGatewayFactory {

        private final Object stubImplementation;

        protected StubAwareCommandGatewayFactory(Builder builder) {
            super(builder);
            this.stubImplementation = builder.stubImplementation;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected <R> InvocationHandler<R> wrapToWaitForResult(final InvocationHandler<CompletableFuture<R>> delegate) {
            return new ReturnResultFromStub<>(delegate, stubImplementation);
        }

        @Override
        protected <R> InvocationHandler<R> wrapToReturnWithFixedTimeout(
                InvocationHandler<CompletableFuture<R>> delegate,
                long timeout, TimeUnit timeUnit) {
            return new ReturnResultFromStub<>(delegate, stubImplementation);
        }

        @Override
        protected <R> InvocationHandler<R> wrapToReturnWithTimeoutInArguments(
                InvocationHandler<CompletableFuture<R>> delegate,
                int timeoutIndex, int timeUnitIndex) {
            return new ReturnResultFromStub<>(delegate, stubImplementation);
        }

        public static class Builder extends CommandGatewayFactory.Builder {

            private Object stubImplementation;

            @Override
            public Builder commandBus(CommandBus commandBus) {
                super.commandBus(commandBus);
                return this;
            }

            private Builder stubImplementation(Object stubImplementation) {
                this.stubImplementation = stubImplementation;
                return this;
            }

            public StubAwareCommandGatewayFactory build() {
                return new StubAwareCommandGatewayFactory(this);
            }
        }
    }

    /**
     * Invocation handler that uses a stub implementation (of not {@code null}) to define the value to return from a
     * handler invocation. If none is provided, the returned future is checked for a value. If that future is not "done"
     * (for example because no callback behavior was provided), it returns {@code null}.
     *
     * @param <R> The return type of the method invocation
     */
    private static class ReturnResultFromStub<R> implements CommandGatewayFactory.InvocationHandler<R> {

        private final CommandGatewayFactory.InvocationHandler<CompletableFuture<R>> dispatcher;
        private final Object stubGateway;

        public ReturnResultFromStub(CommandGatewayFactory.InvocationHandler<CompletableFuture<R>> dispatcher,
                                    Object stubGateway) {
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

        private final String aggregateIdentifier;
        private final String type;
        private int sequenceNumber = 0;

        public AggregateEventPublisherImpl(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.type = "Stub_" + aggregateIdentifier;
        }

        @Override
        public ContinuedGivenState published(Object... events) {
            publish(events);
            return SagaTestFixture.this;
        }

        @Override
        public FixtureExecutionResult publishes(Object event) {
            publish(event);
            return fixtureExecutionResult;
        }

        @Override
        public FixtureExecutionResult publishes(Object event, Map<String, ?> metaData) {
            EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(event).andMetaData(metaData);
            publish(eventMessage);

            return fixtureExecutionResult;
        }

        private void publish(Object... events) {
            for (Object event : events) {
                EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(event);
                handleInSaga(new GenericDomainEventMessage<>(type, aggregateIdentifier,
                                                             sequenceNumber++,
                                                             eventMessage.getPayload(),
                                                             eventMessage.getMetaData(),
                                                             eventMessage.getIdentifier(),
                                                             currentTime()));
            }
        }
    }

    private static class MutableFieldFilter implements FieldFilter {

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

    private static class TransienceValidatingResourceInjector extends SimpleResourceInjector {

        private final List<Object> registeredResources;
        private final boolean transienceCheckEnabled;

        public TransienceValidatingResourceInjector(List<Object> registeredResources, boolean transienceCheckEnabled) {
            super(registeredResources);
            this.registeredResources = registeredResources;
            this.transienceCheckEnabled = transienceCheckEnabled;
        }

        @Override
        public void injectResources(Object saga) {
            super.injectResources(saga);
            if (transienceCheckEnabled) {
                StreamSupport.stream(fieldsOf(saga.getClass()).spliterator(), false)
                             .filter(f -> !Modifier.isTransient(f.getModifiers()))
                             .filter(f -> registeredResources.contains(ReflectionUtils.getFieldValue(f, saga)))
                             .findFirst()
                             .ifPresent(field -> {
                                 throw new AssertionError(format(
                                         "Field %s.%s is injected with a resource,"
                                                 + " but it doesn't have the 'transient' modifier."
                                                 + "\nMark field as 'transient' or disable this check using:"
                                                 + "\nfixture.withTransienceCheckDisabled()",
                                         field.getDeclaringClass(),
                                         field.getName()
                                 ));
                             });
            }
        }
    }

    /**
     * Wrapping {@link ResourceInjector} instance. Will first call the {@link TransienceValidatingResourceInjector}, to
     * ensure the fixture's approach of injecting the default classes (like the {@link EventBus} and {@link CommandBus}
     * for example) is maintained. Afterward, the custom {@code ResourceInjector} provided through the {@link
     * #registerResourceInjector(ResourceInjector)} is called. This will (depending on the implementation) inject more
     * resources, as well as potentially override resources already injected by the {@code
     * TransienceValidatingResourceInjector}.
     */
    private static class WrappingResourceInjector implements ResourceInjector {

        private final ResourceInjector customResourceInjector;
        private final TransienceValidatingResourceInjector defaultResourceInjector;

        public WrappingResourceInjector(ResourceInjector customResourceInjector,
                                        TransienceValidatingResourceInjector defaultResourceInjector) {
            this.customResourceInjector = customResourceInjector;
            this.defaultResourceInjector = defaultResourceInjector;
        }

        @Override
        public void injectResources(Object saga) {
            // First call the default, transience checking injector to ensure correct fixture workings
            defaultResourceInjector.injectResources(saga);
            // Then, call the custom injector, to add other resource or override injection by the default injector
            customResourceInjector.injectResources(saga);
        }
    }
}
