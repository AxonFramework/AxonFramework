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

import org.axonframework.configuration.ApplicationConfigurerTestSuite;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.PayloadBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

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

        Optional<EventStore> eventStore = result.getOptionalComponent(EventStore.class);
        assertTrue(eventStore.isPresent());
        assertInstanceOf(SimpleEventStore.class, eventStore.get());

        Optional<EventSink> eventSink = result.getOptionalComponent(EventSink.class);
        assertTrue(eventSink.isPresent());
        assertInstanceOf(SimpleEventStore.class, eventSink.get());
        // By default, the Event Store and the Event Sink should be the same instance.
        assertEquals(eventStore.get(), eventSink.get());

        Optional<Snapshotter> snapshotter = result.getOptionalComponent(Snapshotter.class);
        assertTrue(snapshotter.isPresent());
    }

    @Test
    void registerStatefulCommandHandlingModuleAddsAModuleConfiguration() {
        EventSourcedEntityBuilder<String, Object> testEntityBuilder =
                EventSourcedEntityBuilder.entity(String.class, Object.class)
                                         .entityFactory( c-> EventSourcedEntityFactory.fromIdentifier(id -> null))
                                         .criteriaResolver(c -> (event, ctx) -> EventCriteria.havingAnyTag())
                                         .entityEvolver(c -> (entity, event, context) -> entity);
        ModuleBuilder<StatefulCommandHandlingModule> statefulCommandHandlingModule =
                StatefulCommandHandlingModule.named("test")
                                             .entities(entityPhase -> entityPhase.entity(testEntityBuilder))
                                             .commandHandlers(commandHandlerPhase -> commandHandlerPhase.commandHandler(
                                                     new QualifiedName(String.class),
                                                     (command, stateManager, context) -> MessageStream.empty().cast()
                                             ));

        List<Configuration> moduleConfigurations =
                testSubject.registerStatefulCommandHandlingModule(statefulCommandHandlingModule)
                           .build()
                           .getModuleConfigurations();

        assertFalse(moduleConfigurations.isEmpty());
        assertEquals(1, moduleConfigurations.size());
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

        Configuration result = testSubject.registerEventStore(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(EventStore.class));
    }

    @Test
    void registerSnapshotterOverridesDefault() {
        Snapshotter expected = (aggregateType, aggregateIdentifier) -> {

        };

        Configuration result = testSubject.registerSnapshotter(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(Snapshotter.class));
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
}