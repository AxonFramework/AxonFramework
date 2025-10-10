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

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.InterceptingEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    void orderEqualsEnhancerOrderConstant() {
        assertEquals(EventSourcingConfigurationDefaults.ENHANCER_ORDER, testSubject.order());
    }

    @Test
    void enhanceSetsExpectedDefaultsInAbsenceOfTheseComponents() {
        ApplicationConfigurer configurer = MessagingConfigurer.create();
        configurer.componentRegistry(cr -> cr.registerEnhancer(testSubject));
        Configuration resultConfig = configurer.build();

        assertInstanceOf(AnnotationBasedTagResolver.class, resultConfig.getComponent(TagResolver.class));
        assertInstanceOf(InMemoryEventStorageEngine.class,
                         resultConfig.getComponent(EventStorageEngine.class));

        // Intercepting at all times, since we have a MessageOriginProvider that leads to the CorrelationDataInterceptor
        EventStore eventStore = resultConfig.getComponent(EventStore.class);
        assertInstanceOf(InterceptingEventStore.class, eventStore);

        EventSink eventSink = resultConfig.getComponent(EventSink.class);
        assertInstanceOf(InterceptingEventStore.class, eventSink);
        // By default, the Event Store and the Event Sink should be the same instance.
        assertEquals(eventStore, eventSink);
        assertInstanceOf(InterceptingEventStore.class, eventSink);

        StreamableEventSource<EventMessage> eventSource = resultConfig.getComponent(StreamableEventSource.class);
        assertInstanceOf(InterceptingEventStore.class, eventSource);
        // By default, the Event Store and the Event Sink should be the same instance.
        assertEquals(eventStore, eventSource);
        assertInstanceOf(InterceptingEventStore.class, eventSource);
    }

    @Test
    void enhanceOnlySetsDefaultsThatAreNotPresentYet() {
        TestTagResolver testTagResolver = new TestTagResolver();

        ApplicationConfigurer configurer = MessagingConfigurer.create();
        configurer.componentRegistry(cr -> cr.registerComponent(TagResolver.class, c -> testTagResolver));
        configurer.componentRegistry(cr -> cr.registerEnhancer(testSubject));

        TagResolver configuredTagResolver = configurer.build()
                                                      .getComponent(TagResolver.class);

        assertEquals(testTagResolver, configuredTagResolver);
    }

    @Test
    void decoratorsEventStoreAsInterceptorEventStoreWhenDispatchInterceptorIsPresent() {
        //noinspection unchecked
        MessagingConfigurer configurer =
                MessagingConfigurer.create()
                                   .registerDispatchInterceptor(c -> mock(MessageDispatchInterceptor.class));

        Configuration resultConfig = configurer.build();

        assertThat(resultConfig.getComponent(EventStore.class)).isInstanceOf(InterceptingEventStore.class);
    }

    private static class TestTagResolver implements TagResolver {

        @Override
        public Set<Tag> resolve(@Nonnull EventMessage event) {
            throw new UnsupportedOperationException();
        }
    }
}