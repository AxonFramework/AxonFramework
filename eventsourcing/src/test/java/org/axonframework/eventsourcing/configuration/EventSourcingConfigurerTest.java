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

import org.axonframework.configuration.ConfigurerTestSuite;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.PayloadBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.configuration.ModellingConfigurer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EventSourcingConfigurer}.
 *
 * @author Steven van Beelen
 */
class EventSourcingConfigurerTest extends ConfigurerTestSuite<EventSourcingConfigurer> {

    @Override
    public EventSourcingConfigurer testSubject() {
        return testSubject == null ? EventSourcingConfigurer.create() : testSubject;
    }

    @Override
    public NewConfiguration build() {
        return testSubject().build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable Class<ModellingConfigurer> delegateType() {
        return ModellingConfigurer.class;
    }

    @Test
    void defaultComponents() {
        NewConfiguration result = testSubject.build();

        Optional<TagResolver> tagResolver = result.getOptionalComponent(TagResolver.class);
        assertTrue(tagResolver.isPresent());
        assertInstanceOf(AnnotationBasedTagResolver.class, tagResolver.get());

        Optional<AsyncEventStorageEngine> eventStorageEngine =
                result.getOptionalComponent(AsyncEventStorageEngine.class);
        assertTrue(eventStorageEngine.isPresent());
        assertInstanceOf(AsyncInMemoryEventStorageEngine.class, eventStorageEngine.get());

        Optional<AsyncEventStore> eventStore = result.getOptionalComponent(AsyncEventStore.class);
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
                                         .entityFactory(c -> (entityType, id) -> null)
                                         .criteriaResolver(c -> event -> EventCriteria.anyEvent())
                                         .eventStateApplier(c -> (model, event, context) -> model);
        ModuleBuilder<StatefulCommandHandlingModule> statefulCommandHandlingModule =
                StatefulCommandHandlingModule.named("test")
                                             .entities(entityPhase -> entityPhase.entity(testEntityBuilder))
                                             .commandHandlers(commandHandlerPhase -> commandHandlerPhase.commandHandler(
                                                     new QualifiedName(String.class),
                                                     (command, stateManager, context) -> MessageStream.empty().cast()
                                             ));

        List<NewConfiguration> moduleConfigurations =
                testSubject.registerStatefulCommandHandlingModule(statefulCommandHandlingModule)
                           .build()
                           .getModuleConfigurations();

        assertFalse(moduleConfigurations.isEmpty());
        assertEquals(1, moduleConfigurations.size());
    }

    @Test
    void registerTagResolverOverridesDefault() {
        TagResolver expected = PayloadBasedTagResolver.forPayloadType(String.class);

        NewConfiguration result = testSubject.registerTagResolver(c -> expected)
                                             .build();

        assertEquals(expected, result.getComponent(TagResolver.class));
    }

    @Test
    void registerEventStorageEngineOverridesDefault() {
        AsyncEventStorageEngine expected = new AsyncInMemoryEventStorageEngine();

        NewConfiguration result = testSubject.registerEventStorageEngine(c -> expected)
                                             .build();

        assertEquals(expected, result.getComponent(AsyncEventStorageEngine.class));
    }

    @Test
    void registerEventStoreOverridesDefault() {
        AsyncEventStore expected = new SimpleEventStore(null, null);

        NewConfiguration result = testSubject.registerEventStore(c -> expected)
                                             .build();

        assertEquals(expected, result.getComponent(AsyncEventStore.class));
    }

    @Test
    void registerSnapshotterOverridesDefault() {
        Snapshotter expected = (aggregateType, aggregateIdentifier) -> {

        };

        NewConfiguration result = testSubject.registerSnapshotter(c -> expected)
                                             .build();

        assertEquals(expected, result.getComponent(Snapshotter.class));
    }

    @Test
    void modellingDelegatesTasks() {
        TestComponent result =
                testSubject.modelling(
                                   modelling -> modelling.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                           )
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }

    @Test
    void messagingDelegatesTasks() {
        TestComponent result =
                testSubject.messaging(
                                   messaging -> messaging.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                           )
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }

    @Test
    void applicationDelegatesTasks() {
        TestComponent result =
                testSubject.application(axon -> axon.registerComponent(TestComponent.class, c -> TEST_COMPONENT))
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }
}