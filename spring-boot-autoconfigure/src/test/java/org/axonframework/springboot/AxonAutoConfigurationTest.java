/*
 * Copyright (c) 2010-2018. Axon Framework
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

/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.springboot;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.Configurer;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.annotation.FixedValueParameterResolver;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.SimpleCorrelationDataProvider;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.spring.stereotype.Saga;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singleton;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@ContextConfiguration(classes = AxonAutoConfigurationTest.Context.class)
@EnableAutoConfiguration(exclude = {JmxAutoConfiguration.class, WebClientAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, DataSourceAutoConfiguration.class})
@RunWith(SpringRunner.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class AxonAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Configurer configurer;

    @Autowired
    private AxonConfiguration configuration;

    @Autowired
    private Serializer serializer;

    @Test
    public void testContextInitialization() {
        assertNotNull(applicationContext);
        assertNotNull(configurer);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(QueryBus.class));
        assertNotNull(applicationContext.getBean(EventStore.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertEquals(MultiParameterResolverFactory.class,
                     applicationContext.getBean(ParameterResolverFactory.class).getClass());
        assertEquals(1, applicationContext.getBeansOfType(EventStorageEngine.class).size());
        assertEquals(0, applicationContext.getBeansOfType(TokenStore.class).size());
        assertNotNull(applicationContext.getBean(Context.MySaga.class));
        assertNotNull(applicationContext.getBean(Context.MyAggregate.class));
        assertNotNull(applicationContext.getBean(EventProcessingConfiguration.class));

        assertEquals(2, configuration.correlationDataProviders().size());

        Context.SomeComponent someComponent = applicationContext.getBean(Context.SomeComponent.class);
        assertEquals(0, someComponent.invocations.size());
        applicationContext.getBean(EventBus.class).publish(asEventMessage("testing"));
        assertEquals(1, someComponent.invocations.size());

        assertTrue(serializer instanceof XStreamSerializer);
        assertSame(applicationContext.getBean("eventSerializer"), applicationContext.getBean("messageSerializer"));
        assertSame(applicationContext.getBean("serializer"), applicationContext.getBean("messageSerializer"));
    }

    @Configuration
    public static class Context {

        @Autowired
        public void configure(EventProcessingConfigurer eventProcessingConfigurer) {
            eventProcessingConfigurer.usingSubscribingEventProcessors();
        }

        @Bean
        public SnapshotTriggerDefinition snapshotTriggerDefinition() {
            return new EventCountSnapshotTriggerDefinition(mock(Snapshotter.class), 2);
        }

        @Bean
        public ParameterResolverFactory customerParameterResolverFactory() {
            return new SimpleResourceParameterResolverFactory(singleton(new CustomResource()));
        }

        @Bean
        public EventStore eventStore() {
            return EmbeddedEventStore.builder().storageEngine(storageEngine()).build();
        }

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

        @Aggregate(snapshotTriggerDefinition = "snapshotTriggerDefinition")
        public static class MyAggregate {

            @CommandHandler
            public void handle(String type, SomeComponent test, CustomResource resource) {

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

        @Saga
        public static class MyDefaultConfigSaga {

            @SagaEventHandler(associationProperty = "toString")
            public void handle(String type, SomeComponent test) {

            }
        }

        @Component
        public static class CustomParameterResolverFactory implements ParameterResolverFactory {

            private final EventBus eventBus;

            @Autowired
            public CustomParameterResolverFactory(EventBus eventBus) {
                this.eventBus = eventBus;
            }

            @Override
            public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
                if (Integer.class.isAssignableFrom(parameters[parameterIndex].getType())) {
                    return new FixedValueParameterResolver<>(1);
                }
                return null;
            }
        }

        @Component
        public static class SomeComponent {

            private List<String> invocations = new ArrayList<>();

            @EventHandler
            public void handle(String event, SomeOtherComponent test, Integer testing) {
                invocations.add(event);
            }
        }

        @Component
        public static class SomeOtherComponent {

        }
    }

    public static class CustomResource {

    }
}
