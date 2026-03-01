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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.eventsourcing.eventstore.EventStore;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ConfigurationEnhancer that registers {@link RecordingEventStore}, {@link RecordingEventSink} and
 * {@link RecordingCommandBus}. The recorded messages can then be used to assert expectations with test cases.
 * <p>
 * Recording decorators are registered as the <b>innermost</b> decorators ({@code DECORATION_ORDER = Integer.MIN_VALUE})
 * so that they capture messages <em>after</em> dispatch interceptors have enriched them (e.g., with correlation
 * metadata). The recording instances are accessible via {@link #recordingCommandBus()} and
 * {@link #recordingEventSink()} after the configuration has been built.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MessagesRecordingConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * Innermost position — recording sees the message after all other decorators (interceptors) have processed it.
     */
    private static final int DECORATION_ORDER = Integer.MIN_VALUE;

    private final AtomicReference<RecordingCommandBus> recordingCommandBus = new AtomicReference<>();
    private final AtomicReference<RecordingEventSink> recordingEventSink = new AtomicReference<>();

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        Objects.requireNonNull(registry, "Cannot enhance a null ComponentRegistry.");

        registry.registerDecorator(CommandBus.class,
                                   DECORATION_ORDER,
                                   (config, name, delegate) -> {
                                       var recording = new RecordingCommandBus(delegate);
                                       recordingCommandBus.set(recording);
                                       return recording;
                                   });

        if (registry.hasComponent(EventStore.class)) {
            registry.registerDecorator(EventStore.class,
                                       DECORATION_ORDER,
                                       (config, name, delegate) -> {
                                           var recording = new RecordingEventStore(delegate);
                                           recordingEventSink.set(recording);
                                           return recording;
                                       });
        } else {
            registry.registerDecorator(EventBus.class,
                                       DECORATION_ORDER,
                                       (config, name, delegate) -> {
                                           var recording = new RecordingEventBus(delegate);
                                           recordingEventSink.set(recording);
                                           return recording;
                                       });
        }
    }

    /**
     * Returns the {@link RecordingCommandBus} instance created by this enhancer. Only available after the configuration
     * has been built and the {@link CommandBus} component has been resolved.
     *
     * @return The recording command bus, or {@code null} if not yet resolved.
     */
    RecordingCommandBus recordingCommandBus() {
        return recordingCommandBus.get();
    }

    /**
     * Returns the {@link RecordingEventSink} instance created by this enhancer. Only available after the configuration
     * has been built and the event sink component has been resolved.
     *
     * @return The recording event sink, or {@code null} if not yet resolved.
     */
    RecordingEventSink recordingEventSink() {
        return recordingEventSink.get();
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }
}
