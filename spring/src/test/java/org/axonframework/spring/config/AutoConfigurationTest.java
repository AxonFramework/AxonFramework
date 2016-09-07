package org.axonframework.spring.config;

import org.axonframework.commandhandling.AsynchronousCommandBus;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.StartSaga;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
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
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class AutoConfigurationTest {

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
    private MyEventHandler myEventHandler;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void contextWiresMainComponents() throws Exception {
        assertNotNull(axonConfig);
        assertNotNull(axonConfig.eventBus());
        assertNotNull(eventBus);
        assertNotNull(eventStore);
        assertNotNull(commandBus);
        assertTrue("Expected Axon to have configured an EventStore", eventBus instanceof EventStore);

        assertTrue("Expected provided commandbus implementation", commandBus instanceof AsynchronousCommandBus);

        assertNotNull(axonConfig.repository(Context.MyAggregate.class));
    }

    @Test
    public void testEventHandlerIsRegistered() {
        eventBus.publish(asEventMessage("Testing 123"));

        assertTrue(myEventHandler.received.contains("Testing 123"));
    }

    @Test
    public void testSagaManagerIsRegistered() {
        eventBus.publish(asEventMessage(new SomeEvent("id")));

        assertTrue(Context.MySaga.events.contains("id"));
        assertEquals(1, customSagaStore.findSagas(Context.MySaga.class, new AssociationValue("id", "id")).size());
        assertEquals(0, sagaStore.findSagas(Context.MySaga.class, new AssociationValue("id", "id")).size());
    }

    @Test
    public void testWiresCommandHandler() {
        FutureCallback<Object, Object> callback = new FutureCallback<>();
        commandBus.dispatch(asCommandMessage("test"), callback);
        callback.getResult(1, TimeUnit.SECONDS);

        Context.MyCommandHandler ch = applicationContext.getBean(Context.MyCommandHandler.class);
        assertTrue(ch.getCommands().contains("test"));
    }

    @EnableAxonAutoConfiguration
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
        public MyEventHandler myEventHandler() {
            return new MyEventHandler();
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

        @Aggregate
        public static class MyAggregate {

            @AggregateIdentifier
            private String id;

            @EventSourcingHandler
            public void on(String event) {
                fail("Event Handler on aggregate shouldn't be invoked");
            }

        }

        @Saga(sagaStore = "customSagaStore")
        public static class MySaga {

            private static List<String> events = new ArrayList<>();

            @StartSaga
            @SagaEventHandler(associationProperty = "id")
            public void handle(SomeEvent event) {
                events.add(event.getId());
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

    public static class MyEventHandler {

        private List<String> received = new ArrayList<>();

        @EventHandler
        public void on(String event) {
            received.add(event);
        }

    }
}
