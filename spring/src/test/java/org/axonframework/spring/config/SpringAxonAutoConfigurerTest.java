/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.commandhandling.model.inspection.MethodCommandHandlerDefinition;
import org.axonframework.commandhandling.model.inspection.MethodCommandHandlerInterceptorDefinition;
import org.axonframework.config.SagaConfiguration;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.replay.ReplayAwareMessageHandlerWrapper;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.SagaMethodMessageHandlerDefinition;
import org.axonframework.eventhandling.saga.StartSaga;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.annotation.*;
import org.axonframework.queryhandling.annotation.MethodQueryMessageHandlerDefinition;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.spring.stereotype.Saga;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SpringAxonAutoConfigurerTest {

    @Autowired(required = false)
    private EventStore eventStore;

    @Autowired(required = false)
    private EventBus eventBus;

    @Autowired(required = false)
    private CommandBus commandBus;

    @Qualifier("customSagaStore")
    @Autowired(required = false)
    private SagaStore<Object> customSagaStore;

    @Qualifier("sagaStore")
    @Autowired(required = false)
    private SagaStore<Object> sagaStore;

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
    private SagaConfiguration<Context.MySaga> mySagaConfiguration;

    @Autowired
    private EventUpcaster eventUpcaster;

    @Autowired
    @Qualifier("myCommandTargetResolver")
    private CommandTargetResolver myCommandTargetResolver;

    @Autowired
    @Qualifier("primaryCommandTargetResolver")
    private CommandTargetResolver primaryCommandTargetResolver;

    @Test
    public void contextWiresMainComponents() {
        assertNotNull(axonConfig);
        assertNotNull(axonConfig.eventBus());
        assertNotNull(eventBus);
        assertNotNull(eventStore);
        assertNotNull(commandBus);
        assertNotNull(mySagaConfiguration);
        assertTrue("Expected Axon to have configured an EventStore", eventBus instanceof EventStore);

        assertTrue("Expected provided commandbus implementation", commandBus instanceof AsynchronousCommandBus);

        assertNotNull(axonConfig.repository(Context.MyAggregate.class));
    }

    @Test
    public void testEventHandlerIsRegistered() {
        eventBus.publish(asEventMessage("Testing 123"));

        assertNotNull("Expected EventBus to be wired", myEventHandler.eventBus);
        assertTrue(myEventHandler.received.contains("Testing 123"));
        assertTrue(myOtherEventHandler.received.contains("Testing 123"));
    }

    @Test
    public void testSagaIsConfigured() {
        AtomicInteger counter = new AtomicInteger();
        mySagaConfiguration.registerHandlerInterceptor(config -> (uow, chain) -> {
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
    public void testWiresCommandHandler() {
        FutureCallback<Object, Object> callback = new FutureCallback<>();
        commandBus.dispatch(asCommandMessage("test"), callback);
        callback.getResult(1, TimeUnit.SECONDS);

        FutureCallback<Object, Object> callback2 = new FutureCallback<>();
        commandBus.dispatch(asCommandMessage("test"), callback2);
        commandBus.dispatch(asCommandMessage(1L), callback2);
        callback.getResult(1, TimeUnit.SECONDS);

        Context.MyCommandHandler ch = applicationContext.getBean(Context.MyCommandHandler.class);
        assertTrue(ch.getCommands().contains("test"));
        verify(primaryCommandTargetResolver).resolveTarget(any());
    }

    @Test
    public void testCustomCommandTargetResolverWiring() {
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
    public void testListenerInvocationErrorHandler() {
        eventBus.publish(asEventMessage("Testing 123"));

        assertNotNull("Expected EventBus to be wired", myEventHandler.eventBus);
        assertFalse(myListenerInvocationErrorHandler.received.isEmpty());
    }

    @Test
    public void testSagaInvocationErrorHandler() {
        eventBus.publish(asEventMessage(new SomeEvent("id")));
        eventBus.publish(asEventMessage(new SomeEventWhichHandlingFails("id")));

        assertTrue(Context.MySaga.events.containsAll(Arrays.asList("id", "id")));
        assertEquals(1, myListenerInvocationErrorHandler.received.size());
        assertEquals("Ooops! I failed.", myListenerInvocationErrorHandler.received.get(0).getMessage());
    }

    @Test
    public void testHandlerDefinitionAndHandlerEnhancerBeansRegistered() {
        MultiHandlerDefinition handlerDefinition = (MultiHandlerDefinition) axonConfig.handlerDefinition(getClass());
        MultiHandlerEnhancerDefinition handlerEnhancerDefinition = (MultiHandlerEnhancerDefinition) handlerDefinition
                .getHandlerEnhancerDefinition();

        assertEquals(AnnotatedMessageHandlingMemberDefinition.class,
                     handlerDefinition.getDelegates().get(0).getClass());
        assertEquals(Context.HandlerDefinitionWithInjectedResource.class,
                     handlerDefinition.getDelegates().get(1).getClass());
        assertEquals(MyHandlerDefinition.class, handlerDefinition.getDelegates().get(2).getClass());
        assertEquals(MyHandlerDefinition.class, handlerDefinition.getDelegates().get(3).getClass());

        assertEquals(SagaMethodMessageHandlerDefinition.class,
                     handlerEnhancerDefinition.getDelegates().get(0).getClass());
        assertEquals(MethodCommandHandlerDefinition.class, handlerEnhancerDefinition.getDelegates().get(1).getClass());
        assertEquals(MethodQueryMessageHandlerDefinition.class,
                     handlerEnhancerDefinition.getDelegates().get(2).getClass());
        assertEquals(ReplayAwareMessageHandlerWrapper.class,
                     handlerEnhancerDefinition.getDelegates().get(3).getClass());
        assertEquals(MethodCommandHandlerInterceptorDefinition.class,
                     handlerEnhancerDefinition.getDelegates().get(4).getClass());
        assertEquals(MyHandlerEnhancerDefinition.class, handlerEnhancerDefinition.getDelegates().get(5).getClass());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEventUpcasterBeanPickedUp() {
        Stream<IntermediateEventRepresentation> representationStream = mock(Stream.class);
        axonConfig.upcasterChain().upcast(representationStream);
        verify(eventUpcaster).upcast(representationStream);
    }

    @EnableAxon
    @Scope
    @Configuration
    public static class Context {

        @Primary
        @Bean(destroyMethod = "shutdown")
        public CommandBus commandBus() {
            return new AsynchronousCommandBus();
        }

        @Bean
        public CommandBus simpleCommandBus() {
            return new SimpleCommandBus();
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

        @Aggregate
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

            public MyOtherAggregate(){
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

        @Saga
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

            public List<String> received = new ArrayList<>();
            private EventBus eventBus;

            @Autowired
            public MyEventHandler(EventBus eventBus) {
                this.eventBus = eventBus;
            }

            @EventHandler
            public void handle(String event, MyOtherEventHandler beanInjectionCheck) {
                assertNotNull(eventBus);
                assertNotNull(beanInjectionCheck);
                received.add(event);
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
            public void onError(Exception exception, EventMessage<?> event, EventListener eventListener) {
                received.add(exception);
            }
        }

        @Bean
        public SagaConfiguration mySagaConfiguration(@Qualifier("customSagaStore") SagaStore<? super MySaga> customSagaStore) {
            return SagaConfiguration.subscribingSagaManager(MySaga.class)
                    .configureSagaStore(c -> customSagaStore);
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
            public <T> Optional<MessageHandlingMember<T>> createHandler(Class<T> declaringType, Executable executable,
                                                                        ParameterResolverFactory parameterResolverFactory) {
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
        public <T> Optional<MessageHandlingMember<T>> createHandler(Class<T> declaringType, Executable executable,
                                                                    ParameterResolverFactory parameterResolverFactory) {
            return Optional.empty();
        }
    }

    private static class MyHandlerEnhancerDefinition implements HandlerEnhancerDefinition {

        @Override
        public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {
            return new MethodCommandHandlerDefinition().wrapHandler(original);
        }
    }
}
