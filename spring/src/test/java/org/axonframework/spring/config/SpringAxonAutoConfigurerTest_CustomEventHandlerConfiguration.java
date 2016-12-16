package org.axonframework.spring.config;

import org.axonframework.commandhandling.AsynchronousCommandBus;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.StartSaga;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.spring.stereotype.Saga;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonMap;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SpringAxonAutoConfigurerTest_CustomEventHandlerConfiguration {

    @Autowired(required = false)
    private EventStore eventStore;

    @Autowired(required = false)
    private EventBus eventBus;

    @Autowired(required = false)
    private CommandBus commandBus;

    @Autowired
    private org.axonframework.config.Configuration axonConfig;

    @Autowired
    private Context.MyEventHandler myEventHandler;

    @Autowired
    private Context.MyOtherEventHandler myOtherEventHandler;

    @Test
    public void testEventHandlerIsRegisteredWithCustomProcessor() throws InterruptedException {
        eventBus.publish(asEventMessage("Testing 123"));

        assertNotNull("Expected EventBus to be wired", myEventHandler.eventBus);
        assertTrue(myEventHandler.received.contains("value"));
        assertTrue(myOtherEventHandler.received.contains("Testing 123"));
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

        @Autowired
        public void configure(EventHandlingConfiguration config) {
            config.byDefaultAssignTo("test")
                    .registerEventProcessor("test", (c, name, eh) -> {
                        SubscribingEventProcessor processor = new SubscribingEventProcessor(name,
                                                                                            new SimpleEventHandlerInvoker(eh),
                                                                                            c.eventBus());
                        processor.registerInterceptor((unitOfWork, interceptorChain) -> {
                            unitOfWork.transformMessage(m -> m.andMetaData(singletonMap("key", "value")));
                            return interceptorChain.proceed();
                        });
                        return processor;
                    });

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

        @Aggregate
        public static class MyAggregate {

            @AggregateIdentifier
            private String id;

            @EventSourcingHandler
            public void on(String event) {
                fail("Event Handler on aggregate shouldn't be invoked");
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

        @Saga(sagaStore = "customSagaStore")
        public static class MySaga {

            private static List<String> events = new ArrayList<>();

            @StartSaga
            @SagaEventHandler(associationProperty = "id")
            public void handle(SomeEvent event) {
                events.add(event.getId());
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

            @EventHandler(payloadType = String.class)
            public void handle(@MetaDataValue("key") String event) {
                assertNotNull(eventBus);
                received.add(event);
            }
        }

        @Component
        public static class MyOtherEventHandler {

            public List<String> received = new ArrayList<>();

            @EventHandler
            public void handle(String event) {
                received.add(event);
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


}
