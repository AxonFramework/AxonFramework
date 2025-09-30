/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.configuration;

import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.configuration.ApplicationConfigurerTestSuite;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.InterceptingEventStore;
import org.axonframework.eventsourcing.eventstore.PayloadBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.correlation.DefaultCorrelationDataProviderRegistry;
import org.axonframework.modelling.configuration.StateBasedEntityModule;
import org.axonframework.queryhandling.configuration.QueryHandlingModule;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EventSourcingConfigurer}.
 *
 * @author Steven van Beelen
 */
class EventSourcingConfigurerTest extends ApplicationConfigurerTestSuite<EventSourcingConfigurer> {

    @Override
    public EventSourcingConfigurer createConfigurer() {
        return testSubject == null ? EventSourcingConfigurer.create() : testSubject;
    }

    @Test
    void defaultComponents() {
        Configuration result = testSubject.build();

        Optional<TagResolver> tagResolver = result.getOptionalComponent(TagResolver.class);
        assertTrue(tagResolver.isPresent());
        assertInstanceOf(AnnotationBasedTagResolver.class, tagResolver.get());

        Optional<EventStorageEngine> eventStorageEngine =
                result.getOptionalComponent(EventStorageEngine.class);
        assertTrue(eventStorageEngine.isPresent());
        assertInstanceOf(InMemoryEventStorageEngine.class, eventStorageEngine.get());

        // Intercepting at all times, since we have a MessageOriginProvider that leads to the CorrelationDataInterceptor
        Optional<EventStore> eventStore = result.getOptionalComponent(EventStore.class);
        assertTrue(eventStore.isPresent());
        assertInstanceOf(InterceptingEventStore.class, eventStore.get());

        Optional<EventSink> eventSink = result.getOptionalComponent(EventSink.class);
        assertTrue(eventSink.isPresent());
        assertInstanceOf(InterceptingEventStore.class, eventSink.get());
        // By default, the Event Store and the Event Sink should be the same instance.
        assertEquals(eventStore.get(), eventSink.get());
    }

    @Test
    void registerTagResolverOverridesDefault() {
        TagResolver expected = PayloadBasedTagResolver.forPayloadType(String.class);

        Configuration result = testSubject.registerTagResolver(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(TagResolver.class));
    }

    @Test
    void registerEventStorageEngineOverridesDefault() {
        EventStorageEngine expected = new InMemoryEventStorageEngine();

        Configuration result = testSubject.registerEventStorageEngine(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(EventStorageEngine.class));
    }

    @Test
    void registerEventStoreOverridesDefault() {
        EventStore expected = new SimpleEventStore(null, null);

        // Registers default provider registry to remove MessageOriginProvider, thus removing CorrelationDataInterceptor.
        // This ensures we don't get an InterceptingEventStore.
        Configuration result = testSubject.componentRegistry(cr -> cr.registerComponent(
                                                  CorrelationDataProviderRegistry.class,
                                                  c -> new DefaultCorrelationDataProviderRegistry())
                                          ).
                                          registerEventStore(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(EventStore.class));
    }

    @Test
    void modellingDelegatesTasks() {
        TestComponent result =
                testSubject.modelling(modelling -> modelling.componentRegistry(
                                   cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                           ))
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }

    @Test
    void messagingDelegatesTasks() {
        TestComponent result =
                testSubject.messaging(messaging -> messaging.componentRegistry(
                                   cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                           ))
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }

    @Test
    void componentRegistryDelegatesTasks() {
        TestComponent result =
                testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT))
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }

    @Test
    void registerEntityModuleAddsAModuleConfiguration() {
        StateBasedEntityModule<String, Object> testEntityBuilder =
                StateBasedEntityModule.declarative(String.class, Object.class)
                                      .loader(c -> (id, context) -> null)
                                      .persister(c -> (id, entity, context) -> null)
                                      .build();

        Configuration configuration =
                testSubject.registerEntity(testEntityBuilder)
                           .build();

        assertThat(configuration.getModuleConfiguration(
                "SimpleStateBasedEntityModule<java.lang.String, java.lang.Object>")).isPresent();
    }

    @Test
    void registerCommandHandlingModuleAddsAModuleConfiguration() {
        ModuleBuilder<CommandHandlingModule> statefulCommandHandlingModule =
                CommandHandlingModule.named("test")
                                     .commandHandlers(commandHandlerPhase -> commandHandlerPhase.commandHandler(
                                             new QualifiedName(String.class),
                                             (command, context) -> MessageStream.empty().cast()
                                     ));

        Configuration configuration =
                testSubject.registerCommandHandlingModule(statefulCommandHandlingModule)
                           .build();

        assertThat(configuration.getModuleConfiguration("test")).isPresent();
    }

    @Test
    void registerQueryHandlingModuleAddsAModuleConfiguration() {
        ModuleBuilder<QueryHandlingModule> statefulCommandHandlingModule =
                QueryHandlingModule.named("test")
                                   .queryHandlers(handlerPhase -> handlerPhase.queryHandler(
                                           new QualifiedName(String.class),
                                           new QualifiedName(String.class),
                                           (command, context) -> MessageStream.empty().cast()
                                   ));

        Configuration configuration =
                testSubject.registerQueryHandlingModule(statefulCommandHandlingModule)
                           .build();

        assertThat(configuration.getModuleConfiguration("test")).isPresent();
    }
}