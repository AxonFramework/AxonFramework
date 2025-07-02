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

package org.axonframework.test.fixture;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.DefaultComponentRegistry;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagesRecordingConfigurationEnhancerTest {

    @Test
    void enhancerRegistersRecordingDecorators() {
        DefaultComponentRegistry componentRegistry = new DefaultComponentRegistry();
        componentRegistry.disableEnhancerScanning()
                         .registerComponent(CommandBus.class, c -> new SimpleCommandBus())
                         .registerComponent(EventStore.class, c -> new SimpleEventStore(
                                 new InMemoryEventStorageEngine(), new AnnotationBasedTagResolver()))
                         .registerEnhancer(new MessagesRecordingConfigurationEnhancer());

        Configuration configuration = componentRegistry.build(mock(LifecycleRegistry.class));

        CommandBus commandBus = configuration.getComponent(CommandBus.class);
        EventStore eventStore = configuration.getComponent(EventStore.class);
        EventSink eventSink = configuration.getComponent(EventSink.class);

        assertInstanceOf(RecordingCommandBus.class, commandBus);
        assertInstanceOf(RecordingEventStore.class, eventStore);
        assertInstanceOf(RecordingEventStore.class, eventSink);
    }

    @Test
    void enhancerIsRegisteredViaServiceLoader() {
        DefaultComponentRegistry componentRegistry = new DefaultComponentRegistry();
        componentRegistry.registerComponent(CommandBus.class, c -> mock(CommandBus.class))
                         .registerComponent(EventStore.class, c -> mock(EventStore.class));

        Configuration configuration = componentRegistry.build(mock(LifecycleRegistry.class));

        CommandBus commandBus = configuration.getComponent(CommandBus.class);
        EventStore eventStore = configuration.getComponent(EventStore.class);
        EventSink eventSink = configuration.getComponent(EventSink.class);

        assertInstanceOf(RecordingCommandBus.class, commandBus);
        assertInstanceOf(RecordingEventStore.class, eventStore);
        assertInstanceOf(RecordingEventStore.class, eventSink);
    }

    @Test
    void enhancerDoesNotDecorateEventSinkWhenItIsAlreadyEventStore() {
        DefaultComponentRegistry componentRegistry = new DefaultComponentRegistry();
        componentRegistry.disableEnhancerScanning()
                         .registerComponent(CommandBus.class, c -> mock(CommandBus.class))
                         .registerComponent(EventStore.class, c -> mock(EventStore.class))
                         .registerEnhancer(new MessagesRecordingConfigurationEnhancer());

        Configuration configuration = componentRegistry.build(mock(LifecycleRegistry.class));

        EventStore eventStore = configuration.getComponent(EventStore.class);
        EventSink eventSink = configuration.getComponent(EventSink.class);

        // When EventSink is the same as EventStore, it should not be decorated separately
        assertSame(eventStore, eventSink);
        assertInstanceOf(RecordingEventStore.class, eventStore);
    }

    @Test
    void enhancerHasMaximumOrder() {
        MessagesRecordingConfigurationEnhancer enhancer = new MessagesRecordingConfigurationEnhancer();
        assertEquals(Integer.MAX_VALUE, enhancer.order());
    }
} 