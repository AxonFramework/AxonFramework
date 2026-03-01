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
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentFactory;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.common.configuration.InstantiatedComponentDefinition;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.eventsourcing.eventstore.EventStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ConfigurationEnhancer that registers {@link RecordingEventStore}, {@link RecordingEventSink} and
 * {@link RecordingCommandBus}. The recorded messages can then be used to assert expectations with test cases.
 * <p>
 * Recording decorators are registered as the <b>innermost</b> decorators ({@code DECORATION_ORDER = Integer.MIN_VALUE})
 * so that they capture messages <em>after</em> dispatch interceptors have enriched them (e.g., with correlation
 * metadata).
 * <p>
 * The recording instances are also exposed as {@link ComponentFactory ComponentFactories} under their exact types, so
 * they can be resolved from the {@link Configuration} via
 * {@code configuration.getComponent(RecordingCommandBus.class, RECORDING_COMPONENT_NAME)} and
 * {@code configuration.getComponent(RecordingEventSink.class, RECORDING_COMPONENT_NAME)}.
 * <p>
 * Using {@code ComponentFactory} (rather than regular component registration) is necessary because
 * {@code RecordingCommandBus implements CommandBus}, and registering it as a regular component would cause
 * {@code CommandBus} decorators to match it via {@code isAssignableFrom}, leading to a {@code ClassCastException}.
 * Factory-produced components are created on-demand after the decoration phase, so they are never subject to
 * type-assignability decorator matching.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MessagesRecordingConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * The component name used to register and resolve the recording instances from the {@link Configuration}.
     * <p>
     * Use this constant when resolving recording components:
     * <pre>{@code
     * configuration.getComponent(RecordingCommandBus.class, RECORDING_COMPONENT_NAME);
     * configuration.getComponent(RecordingEventSink.class, RECORDING_COMPONENT_NAME);
     * }</pre>
     */
    public static final String RECORDING_COMPONENT_NAME = "recording";

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

        // Factories expose recording instances under their exact types.
        // Factory-produced components are created on-demand (after decoration), so they are
        // never subject to type-assignability decorator matching.
        registry.registerFactory(new RecordingCommandBusFactory());
        registry.registerFactory(new RecordingEventSinkFactory());
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    private class RecordingCommandBusFactory implements ComponentFactory<RecordingCommandBus> {

        @Override
        @Nonnull
        public Class<RecordingCommandBus> forType() {
            return RecordingCommandBus.class;
        }

        @Override
        @Nonnull
        public Optional<Component<RecordingCommandBus>> construct(@Nonnull String name,
                                                                   @Nonnull Configuration config) {
            // Trigger CommandBus resolution to ensure the decorator populates the AtomicReference
            config.getComponent(CommandBus.class);
            var recording = recordingCommandBus.get();
            if (recording == null) {
                return Optional.empty();
            }
            return Optional.of(new InstantiatedComponentDefinition<>(
                    new Component.Identifier<>(forType(), name), recording
            ));
        }

        @Override
        public void registerShutdownHandlers(@Nonnull LifecycleRegistry registry) {
            // Nothing to do here
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("type", forType());
        }
    }

    private class RecordingEventSinkFactory implements ComponentFactory<RecordingEventSink> {

        @Override
        @Nonnull
        public Class<RecordingEventSink> forType() {
            return RecordingEventSink.class;
        }

        @Override
        @Nonnull
        public Optional<Component<RecordingEventSink>> construct(@Nonnull String name,
                                                                  @Nonnull Configuration config) {
            // Trigger EventSink resolution to ensure the decorator populates the AtomicReference
            config.getComponent(EventSink.class);
            var recording = recordingEventSink.get();
            if (recording == null) {
                return Optional.empty();
            }
            return Optional.of(new InstantiatedComponentDefinition<>(
                    new Component.Identifier<>(forType(), name), recording
            ));
        }

        @Override
        public void registerShutdownHandlers(@Nonnull LifecycleRegistry registry) {
            // Nothing to do here
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("type", forType());
        }
    }
}
