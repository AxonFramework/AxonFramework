package org.axonframework.boot;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.Configurer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.SimpleCorrelationDataProvider;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.spring.stereotype.Saga;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@ContextConfiguration(classes = {
        AxonAutoConfigurationTest.Context.class,
        DataSourceAutoConfiguration.class,
        AxonAutoConfiguration.class})
@RunWith(SpringRunner.class)
public class AxonAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Configurer configurer;

    @Autowired
    private AxonConfiguration configuration;

    @Test
    public void testContextInitialization() throws Exception {
        assertNotNull(applicationContext);
        assertNotNull(configurer);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertEquals(1, applicationContext.getBeansOfType(EventStorageEngine.class).size());
        assertEquals(0, applicationContext.getBeansOfType(TokenStore.class).size());
        assertNotNull(applicationContext.getBean(Context.MySaga.class));
        assertNotNull(applicationContext.getBean(Context.MyAggregate.class));

        assertEquals(2, configuration.correlationDataProviders().size());
    }

    @Configuration
    public static class Context {

        @Bean
        public EventStorageEngine storageEngine() {
            return new InMemoryEventStorageEngine();
        }

        @Bean
        public CorrelationDataProvider correlationData1() {
            return new SimpleCorrelationDataProvider("key1");
        }

        @Bean
        public CorrelationDataProvider correlationData2() {
            return new SimpleCorrelationDataProvider("key2");
        }

        @Aggregate
        public static class MyAggregate {

            @CommandHandler
            public void handle(String type, SomeComponent test) {

            }

            @EventHandler
            public void on(String type, SomeComponent test) {

            }
        }

        @Saga
        public static class MySaga {
            @SagaEventHandler(associationProperty = "toString")
            public void handle(String type, SomeComponent test) {

            }

        }

        @Component
        public static class SomeComponent {

            @EventHandler
            public void handle(String event, SomeOtherComponent test) {

            }

        }

        @Component
        public static class SomeOtherComponent {
        }

    }
}
