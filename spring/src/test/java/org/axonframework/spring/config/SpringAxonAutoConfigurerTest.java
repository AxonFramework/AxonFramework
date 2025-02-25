/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.spring.config;

import com.thoughtworks.xstream.XStream;
import java.lang.reflect.Executable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.axonframework.commandhandling.AsynchronousCommandBus;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.MethodCommandHandlerDefinition;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.TagsConfiguration;
import org.axonframework.deadline.annotation.DeadlineMethodMessageHandlerDefinition;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.replay.ReplayAwareMessageHandlerWrapper;
import org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler;
import org.axonframework.eventsourcing.CachingEventSourcingRepository;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlerInterceptorDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.MultiHandlerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.VersionedAggregateIdentifier;
import org.axonframework.modelling.command.inspection.MethodCommandHandlerInterceptorDefinition;
import org.axonframework.modelling.command.inspection.MethodCreationPolicyDefinition;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.EndSagaMessageHandlerDefinition;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaMethodMessageHandlerDefinition;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.annotation.MethodQueryMessageHandlerDefinition;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.serialization.xml.CompactDriver;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.eventhandling.scheduling.quartz.QuartzEventSchedulerFactoryBean;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.internal.util.collections.Sets;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.test.StepVerifier;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link SpringAxonAutoConfigurer}.
 *
 * @author Allard Buijze
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration
public class SpringAxonAutoConfigurerTest {

    @Autowired(required = false)
    private EventStore eventStore;

    @Autowired(required = false)
    private EventBus eventBus;

    @Autowired(required = false)
    private CommandBus commandBus;

    @Autowired(required = false)
    private QueryBus queryBus;

    @Qualifier("customSagaStore")
    @Autowired(required = false)
    private SagaStore<Object> customSagaStore;

    @Qualifier("sagaStore")
    @Autowired(required = false)
    private SagaStore<Object> sagaStore;

    @Autowired(required = false)
    private EventProcessingConfiguration eventProcessingConfiguration;

    @Autowired(required = false)
    private EventProcessingConfigurer eventProcessingConfigurer;

    @Autowired
    private org.axonframework.config.Configuration axonConfig;

    @Autowired
    private Context.MyEventHandler myEventHandler;

    @Autowired
    private Context.MyOtherEventHandler myOtherEventHandler;

    @Autowired
    private Context.MyListenerInvocationErrorHandler myListenerInvocationErrorHandler;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EventUpcaster eventUpcaster;

    @Autowired
    @Qualifier("myCommandTargetResolver")
    private CommandTargetResolver myCommandTargetResolver;

    @Autowired
    @Qualifier("myCache")
    private Cache myCache;

    @Autowired
    @Qualifier("primaryCommandTargetResolver")
    private CommandTargetResolver primaryCommandTargetResolver;

    @Autowired
    private TagsConfiguration tagsConfiguration;

    @Autowired
    @Qualifier("myLockFactory")
    private LockFactory myLockFactory;

    @Autowired
    private QuartzEventScheduler quartzEventScheduler;

    @Autowired
    @Qualifier("eventSerializer")
    private Serializer eventSerializer;

    @AfterEach
    void tearDown() {
        reset(primaryCommandTargetResolver);
    }

    @Test
    void contextWiresMainComponents() {
        assertNotNull(axonConfig);
        assertNotNull(axonConfig.eventBus());
        assertNotNull(eventBus);
        assertNotNull(eventStore);
        assertNotNull(commandBus);
        assertNotNull(eventProcessingConfigurer);
        assertNotNull(eventProcessingConfiguration);
        assertEquals(eventProcessingConfiguration, axonConfig.eventProcessingConfiguration());
        assertTrue(eventBus instanceof EventStore, "Expected Axon to have configured an EventStore");
        assertTrue(commandBus instanceof AsynchronousCommandBus, "Expected provided CommandBus implementation");
        assertNotNull(axonConfig.repository(Context.MyAggregate.class));
        assertNotNull(tagsConfiguration);
        assertEquals(tagsConfiguration, axonConfig.tags());
        assertNotNull(axonConfig.eventScheduler());
        assertNotNull(quartzEventScheduler);
    }

    @Test
    void testEventHandlerIsRegistered() {
        eventBus.publish(asEventMessage("Testing 123"));

        assertNotNull(myEventHandler.eventBus, "Expected EventBus to be wired");
        assertTrue(myEventHandler.received.contains("Testing 123"));
        assertTrue(myOtherEventHandler.received.contains("Testing 123"));
    }

    @Test
    void testSagaIsConfigured() {
        AtomicInteger counter = new AtomicInteger();
        eventProcessingConfigurer.registerHandlerInterceptor("MySagaProcessor", config -> (uow, chain) -> {
            counter.incrementAndGet();
            return chain.proceed();
        });
        eventBus.publish(asEventMessage(new SomeEvent("id")));

        assertTrue(Context.MySaga.events.contains("id"));
        assertEquals(1, customSagaStore.findSagas(Context.MySaga.class, new AssociationValue("id", "id")).size());
        assertEquals(0, sagaStore.findSagas(Context.MySaga.class, new AssociationValue("id", "id")).size());
        assertEquals(1, counter.get());
    }

    @Test
    void testWiresCommandHandler() {
        FutureCallback<Object, Object> callback = new FutureCallback<>();
        commandBus.dispatch(asCommandMessage("test"), callback);
        callback.getResult(1, TimeUnit.SECONDS);

        FutureCallback<Object, Object> callback2 = new FutureCallback<>();
        commandBus.dispatch(asCommandMessage("test"), callback2);
        commandBus.dispatch(asCommandMessage(1L), callback2);
        callback.getResult(1, TimeUnit.SECONDS);

        Context.MyCommandHandler ch = applicationContext.getBean(Context.MyCommandHandler.class);
        assertTrue(ch.getCommands().contains("test"));
        verify(primaryCommandTargetResolver, timeout(500)).resolveTarget(any());
    }

    @Test
    void testCustomCommandTargetResolverWiring() {
        FutureCallback<Object, Object> callback1 = new FutureCallback<>();
        commandBus.dispatch(asCommandMessage(new Context.CreateMyOtherAggregateCommand("id")), callback1);
        callback1.getResult();

        when(myCommandTargetResolver.resolveTarget(any())).thenReturn(new VersionedAggregateIdentifier("id", null));

        FutureCallback<Object, Object> callback2 = new FutureCallback<>();
        commandBus.dispatch(asCommandMessage(new Context.UpdateMyOtherAggregateCommand("id")), callback2);
        callback2.getResult();

        verify(myCommandTargetResolver).resolveTarget(any());
    }

    @Test
    void testListenerInvocationErrorHandler() {
        eventBus.publish(asEventMessage("Testing 123"));

        assertNotNull(myEventHandler.eventBus, "Expected EventBus to be wired");
        assertFalse(myListenerInvocationErrorHandler.received.isEmpty());
    }

    @Test
    void testSagaInvocationErrorHandler() {
        eventBus.publish(asEventMessage(new SomeEvent("id")));
        eventBus.publish(asEventMessage(new SomeEventWhichHandlingFails("id")));

        assertTrue(Context.MySaga.events.containsAll(Arrays.asList("id", "id")));
        assertEquals(1, myListenerInvocationErrorHandler.received.size());
        assertEquals("Ooops! I failed.", myListenerInvocationErrorHandler.received.get(0).getMessage());
    }

    @Test
    void testWiringOfQueryHandlerAndQueryUpdateEmitter() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "axonCR",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));

        SubscriptionQueryResult<QueryResponseMessage<List<String>>, SubscriptionQueryUpdateMessage<String>> result = queryBus
                .subscriptionQuery(queryMessage);
        eventBus.publish(asEventMessage("New chat message"));

        StepVerifier.create(result.initialResult().map(Message::getPayload))
                    .expectNext(Arrays.asList("Message1", "Message2", "Message3"))
                    .verifyComplete();
        StepVerifier.create(result.updates().map(Message::getPayload))
                    .expectNext("New chat message")
                    .verifyComplete();
    }

    @Test
    void testHandlerDefinitionAndHandlerEnhancerBeansRegistered() {
        MultiHandlerDefinition handlerDefinition = (MultiHandlerDefinition) axonConfig.handlerDefinition(getClass());
        MultiHandlerEnhancerDefinition handlerEnhancerDefinition =
                (MultiHandlerEnhancerDefinition) handlerDefinition.getHandlerEnhancerDefinition();

        assertEquals(
                AnnotatedMessageHandlingMemberDefinition.class, handlerDefinition.getDelegates().get(0).getClass()
        );
        assertEquals(
                Context.HandlerDefinitionWithInjectedResource.class, handlerDefinition.getDelegates().get(1).getClass()
        );
        assertEquals(MyHandlerDefinition.class, handlerDefinition.getDelegates().get(2).getClass());
        assertEquals(MyHandlerDefinition.class, handlerDefinition.getDelegates().get(3).getClass());

        Set<Class<?>> enhancerClasses = handlerEnhancerDefinition.getDelegates()
                                                                 .stream()
                                                                 .map(HandlerEnhancerDefinition::getClass)
                                                                 .collect(Collectors.toSet());

        assertEquals(
                Sets.newSet(
                        SagaMethodMessageHandlerDefinition.class,
                        MethodCommandHandlerInterceptorDefinition.class,
                        MethodCommandHandlerDefinition.class,
                        MethodCommandHandlerDefinition.class,
                        MethodQueryMessageHandlerDefinition.class,
                        ReplayAwareMessageHandlerWrapper.class,
                        DeadlineMethodMessageHandlerDefinition.class,
                        MethodCreationPolicyDefinition.class,
                        MethodCreationPolicyDefinition.class,
                        MyHandlerEnhancerDefinition.class,
                        MessageHandlerInterceptorDefinition.class,
                        EndSagaMessageHandlerDefinition.class
                ),
                enhancerClasses
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void testEventUpcasterBeanPickedUp() {
        Stream<IntermediateEventRepresentation> representationStream = mock(Stream.class);
        axonConfig.upcasterChain().upcast(representationStream);
        verify(eventUpcaster).upcast(representationStream);
    }

    @Test
    void testAggregateCaching() {
        FutureCallback<Object, Object> callback1 = new FutureCallback<>();
        commandBus.dispatch(asCommandMessage(new Context.CreateMyCachedAggregateCommand("id")), callback1);
        callback1.getResult();
        assertNotNull(axonConfig.repository(Context.MyCachedAggregate.class));
        assertEquals(
                CachingEventSourcingRepository.class, axonConfig.repository(Context.MyCachedAggregate.class).getClass()
        );
    }

    @Test
    void testAggregateLockFactory() {
        String expectedAggregateId = "someIdentifier";

        FutureCallback<Object, Object> commandCallback = new FutureCallback<>();
        commandBus.dispatch(asCommandMessage(
                new Context.CreateMyCachedAggregateCommand(expectedAggregateId)), commandCallback
        );
        commandCallback.getResult();

        verify(myLockFactory).obtainLock(expectedAggregateId);
    }

    @Test
    void testEventSchedulerUsesEventSerializer() {
        when(eventSerializer.serialize(any(), eq(byte[].class))).thenReturn(new SimpleSerializedObject<>(new byte[1], byte[].class, SerializedType.emptyType()));
        quartzEventScheduler.schedule(Instant.now(), "deadline");
        //The below check shows we have serialized both the payload and metadata using this serializer
        verify(eventSerializer, times(2)).serialize(any(), any());
    }

    @AnnotationDriven
    @Import({SpringAxonAutoConfigurer.ImportSelector.class, AnnotationDrivenRegistrar.class})
    @Scope
    @Configuration
    public static class Context {

        @Bean
        public EventProcessingModule eventProcessingConfiguration(
                @Qualifier("customSagaStore") SagaStore<? super MySaga> customSagaStore) {
            EventProcessingModule eventProcessingModule = new EventProcessingModule();
            eventProcessingModule.usingSubscribingEventProcessors()
                                 .registerSaga(MySaga.class, sc -> sc.configureSagaStore(conf -> customSagaStore));
            return eventProcessingModule;
        }

        @Bean
        public TagsConfiguration tagsConfiguration() {
            return new TagsConfiguration();
        }

        @Primary
        @Bean(destroyMethod = "shutdown")
        public CommandBus commandBus() {
            return AsynchronousCommandBus.builder().build();
        }

        @Bean
        public CommandBus simpleCommandBus() {
            return SimpleCommandBus.builder().build();
        }

        @Bean
        public EventStorageEngine eventStorageEngine() {
            return new InMemoryEventStorageEngine();
        }

        @Bean
        public SagaStore sagaStore() {
            return new InMemorySagaStore();
        }

        @Bean
        public SagaStore customSagaStore() {
            return new InMemorySagaStore();
        }

        @Bean
        public EventUpcaster eventUpcaster() {
            return mock(EventUpcaster.class);
        }

        @Bean
        @Primary
        @Qualifier("primaryCommandTargetResolver")
        public CommandTargetResolver primaryCommandTargetResolver() {
            return mock(CommandTargetResolver.class);
        }

        @Bean
        @Qualifier("myCommandTargetResolver")
        public CommandTargetResolver myCommandTargetResolver() {
            return mock(CommandTargetResolver.class);
        }

        @Bean
        public Scheduler scheduler() throws SchedulerException {
            Scheduler scheduler = mock(Scheduler.class);
            when(scheduler.getContext()).thenReturn(mock(SchedulerContext.class));
            return scheduler;
        }

        @Bean
        @Primary
        public Serializer serializer() {
            return XStreamSerializer.builder()
                                    .xStream(new XStream(new CompactDriver()))
                                    .build();
        }

        @Bean
        public Serializer eventSerializer() {
            return mock(Serializer.class);
        }

        @Bean
        public QuartzEventSchedulerFactoryBean quartzEventSchedulerFactoryBean() {
            return new QuartzEventSchedulerFactoryBean();
        }

        @Bean
        @Qualifier("myCache")
        public Cache myCache() {
            return mock(Cache.class);
        }

        @Bean
        @Qualifier("myLockFactory")
        public LockFactory myLockFactory() {
            return spy(PessimisticLockFactory.usingDefaults());
        }

        @Aggregate(type = "MyCustomAggregateType", filterEventsByType = true)
        public static class MyAggregate {

            @AggregateIdentifier
            private String id;

            @CommandHandler
            public void handle(Long command, MyEventHandler beanInjection) {
                assertNotNull(beanInjection);
                apply(command);
            }

            @EventSourcingHandler
            public void on(Long event, MyEventHandler beanInjection) {
                assertNotNull(beanInjection);
                this.id = Long.toString(event);
            }

            @EventSourcingHandler
            public void on(String event) {
                fail("Event Handler on aggregate shouldn't be invoked");
            }
        }

        public static class CreateMyOtherAggregateCommand {

            @TargetAggregateIdentifier
            private final String id;

            public CreateMyOtherAggregateCommand(String id) {
                this.id = id;
            }
        }

        public static class UpdateMyOtherAggregateCommand {

            @TargetAggregateIdentifier
            private final String id;

            public UpdateMyOtherAggregateCommand(String id) {
                this.id = id;
            }
        }

        public static class MyOtherAggregateCreatedEvent {

            private final String id;

            public MyOtherAggregateCreatedEvent(String id) {
                this.id = id;
            }
        }

        @Aggregate(commandTargetResolver = "myCommandTargetResolver")
        public static class MyOtherAggregate {

            @AggregateIdentifier
            private String id;

            public MyOtherAggregate() {
                // default constructor
            }

            @CommandHandler
            public MyOtherAggregate(CreateMyOtherAggregateCommand command) {
                apply(new MyOtherAggregateCreatedEvent(command.id));
            }

            @EventSourcingHandler
            public void on(MyOtherAggregateCreatedEvent event) {
                this.id = event.id;
            }

            @CommandHandler
            public void handle(UpdateMyOtherAggregateCommand command) {
                // nothing to do here
            }
        }

        public static class CreateMyCachedAggregateCommand {

            @TargetAggregateIdentifier
            private final String id;

            public CreateMyCachedAggregateCommand(String id) {
                this.id = id;
            }
        }

        public static class MyCachedAggregateCreatedEvent {

            private final String id;

            public MyCachedAggregateCreatedEvent(String id) {
                this.id = id;
            }
        }

        @Aggregate(cache = "myCache", lockFactory = "myLockFactory")
        public static class MyCachedAggregate {

            @AggregateIdentifier
            private String id;

            public MyCachedAggregate() {
                // default constructor
            }

            @CommandHandler
            public MyCachedAggregate(CreateMyCachedAggregateCommand command) {
                apply(new MyCachedAggregateCreatedEvent(command.id));
            }

            @EventSourcingHandler
            public void on(MyCachedAggregateCreatedEvent event) {
                this.id = event.id;
            }
        }

        @Component
        public static class MyCommandHandler {

            private List<String> commands = new ArrayList<>();

            @CommandHandler
            public void handle(String command) {
                commands.add(command);
            }

            public List<String> getCommands() {
                return commands;
            }
        }

        public static class MySaga {

            private static List<String> events = new ArrayList<>();

            @StartSaga
            @SagaEventHandler(associationProperty = "id")
            public void handle(SomeEvent event, MyEventHandler beanInjection) {
                assertNotNull(beanInjection);
                events.add(event.getId());
            }

            @SagaEventHandler(associationProperty = "id")
            public void handle(SomeEventWhichHandlingFails event) {
                events.add(event.getId());
                throw new RuntimeException("Ooops! I failed.");
            }
        }

        @Component
        public static class MyEventHandler {

            public final List<String> received = new ArrayList<>();
            private final EventBus eventBus;
            private final QueryUpdateEmitter queryUpdateEmitter;

            public MyEventHandler(EventBus eventBus, QueryUpdateEmitter queryUpdateEmitter) {
                this.eventBus = eventBus;
                this.queryUpdateEmitter = queryUpdateEmitter;
            }

            @EventHandler
            public void handle(String event, MyOtherEventHandler beanInjectionCheck) {
                assertNotNull(eventBus);
                assertNotNull(beanInjectionCheck);
                received.add(event);
                queryUpdateEmitter.emit(String.class, "axonCR"::equals, event);
                queryUpdateEmitter.complete(String.class, "axonCR"::equals);
            }
        }

        @Component
        public static class MyQueryHandler {

            @QueryHandler
            public List<String> getChatMessages(String chatRoom) {
                return Arrays.asList("Message1", "Message2", "Message3");
            }
        }

        @Component
        public static class MyOtherEventHandler {

            public List<String> received = new ArrayList<>();

            @EventHandler
            public void handle(String event, MyEventHandler beanInjection) {
                assertNotNull(beanInjection);
                received.add(event);
            }
        }

        @Component
        public static class FailingEventHandler {

            @EventHandler
            public void handle(String event) {
                throw new RuntimeException();
            }
        }

        @Component
        public static class MyListenerInvocationErrorHandler implements ListenerInvocationErrorHandler {

            public List<Exception> received = new ArrayList<>();

            @Override
            public void onError(@Nonnull Exception exception, @Nonnull EventMessage<?> event,
                                @Nonnull EventMessageHandler eventHandler) {
                received.add(exception);
            }
        }

        @Bean
        public HandlerDefinition myHandlerDefinition1() {
            return new MyHandlerDefinition();
        }

        @Bean
        public HandlerDefinition myHandlerDefinition2() {
            return new MyHandlerDefinition();
        }

        @Bean
        public HandlerEnhancerDefinition myHandlerEnhancerDefinition() {
            return new MyHandlerEnhancerDefinition();
        }

        @Component
        public static class HandlerDefinitionWithInjectedResource implements HandlerDefinition {

            private final CommandBus commandBus;

            public HandlerDefinitionWithInjectedResource(CommandBus commandBus) {
                this.commandBus = commandBus;
            }

            @Override
            public <T> Optional<MessageHandlingMember<T>> createHandler(@Nonnull Class<T> declaringType,
                                                                        @Nonnull Executable executable,
                                                                        @Nonnull ParameterResolverFactory parameterResolverFactory) {
                assertNotNull(commandBus);
                return Optional.empty();
            }
        }

    }

    public static class SomeEvent {

        private final String id;

        public SomeEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class SomeEventWhichHandlingFails {

        private final String id;

        public SomeEventWhichHandlingFails(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class MyHandlerDefinition implements HandlerDefinition {

        @Override
        public <T> Optional<MessageHandlingMember<T>> createHandler(@Nonnull Class<T> declaringType,
                                                                    @Nonnull Executable executable,
                                                                    @Nonnull ParameterResolverFactory parameterResolverFactory) {
            return Optional.empty();
        }
    }

    private static class MyHandlerEnhancerDefinition implements HandlerEnhancerDefinition {

        @Override
        public @Nonnull
        <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
            return new MethodCommandHandlerDefinition().wrapHandler(original);
        }
    }
}
