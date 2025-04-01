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

import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.AxonApplication;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EventSourcingConfigurationDefaults}.
 *
 * @author Steven van Beelen
 */
class EventSourcingConfigurationDefaultsTest {

    private EventSourcingConfigurationDefaults testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new EventSourcingConfigurationDefaults();
    }

    @Test
    void orderEqualsMaxInteger() {
        assertEquals(Integer.MAX_VALUE - 1, testSubject.order());
    }

    @Test
    void enhanceSetsExpectedDefaultsInAbsenceOfTheseComponents() {
        ApplicationConfigurer<?> configurer = AxonApplication.create();
        testSubject.enhance(configurer);
        NewConfiguration resultConfig = configurer.build();

        assertInstanceOf(AnnotationBasedTagResolver.class, resultConfig.getComponent(TagResolver.class));
        assertInstanceOf(AsyncInMemoryEventStorageEngine.class,
                         resultConfig.getComponent(AsyncEventStorageEngine.class));
        AsyncEventStore eventStore = resultConfig.getComponent(AsyncEventStore.class);
        assertInstanceOf(SimpleEventStore.class, eventStore);
        EventSink eventSink = resultConfig.getComponent(EventSink.class);
        assertInstanceOf(SimpleEventStore.class, eventSink);
        // By default, the Event Store and the Event Sink should be the same instance.
        assertEquals(eventStore, eventSink);
        assertInstanceOf(Snapshotter.class, resultConfig.getComponent(Snapshotter.class));
    }

    @Test
    void enhanceOnlySetsDefaultsThatAreNotPresentYet() {
        TestTagResolver testTagResolver = new TestTagResolver();

        ApplicationConfigurer<?> configurer =
                AxonApplication.create()
                               .registerComponent(TagResolver.class, c -> testTagResolver);

        testSubject.enhance(configurer);

        TagResolver configuredTagResolver = configurer.build()
                                                      .getComponent(TagResolver.class);

        assertEquals(testTagResolver, configuredTagResolver);
    }

    private static class TestTagResolver implements TagResolver {

        @Override
        public Set<Tag> resolve(@NotNull EventMessage<?> event) {
            throw new UnsupportedOperationException();
        }
    }
}