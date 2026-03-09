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
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.eventsourcing.eventstore.EventStore;

import java.util.Objects;

import static org.axonframework.messaging.commandhandling.distributed.DistributedCommandBusConfigurationEnhancer.DISTRIBUTED_COMMAND_BUS_ORDER;

/**
 * ConfigurationEnhancer that registers {@link RecordingEventStore}, {@link RecordingEventSink} and
 * {@link RecordingCommandBus}. The recorded messages can then be used to assert expectations with test cases.
 * <p>
 * Recording decorators are registered as the <b>innermost</b> decorators ({@code DECORATION_ORDER = Integer.MIN_VALUE})
 * so that they capture messages <em>after</em> dispatch interceptors have enriched them (e.g., with correlation
 * metadata).
 * <p>
 * The recording instances are stored in a {@link RecordingComponentsRegistry} that is registered as a regular component.
 * The fixture resolves the registry via {@code configuration.getComponent(RecordingComponentsRegistry.class)} and reads
 * the recording instances from it.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class MessagesRecordingConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * Innermost position — recording sees the message after all other decorators (interceptors) have processed it.
     */
    private static final int EVENTS_RECORDER_DECORATION_ORDER = Integer.MIN_VALUE;

    /**
     * Decoration order for the command bus recorder. Placed just outside the
     * {@link org.axonframework.messaging.commandhandling.distributed.DistributedCommandBus} so that commands are
     * recorded with their original, non-serialized payloads before they reach the distributed bus.
     */
    private static final int COMMANDS_RECORDER_DECORATION_ORDER = DISTRIBUTED_COMMAND_BUS_ORDER + 1;

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        Objects.requireNonNull(registry, "Cannot enhance a null ComponentRegistry.");

        var recordings = new RecordingComponentsRegistry();
        registry.registerComponent(RecordingComponentsRegistry.class, config -> recordings);

        registry.registerDecorator(CommandBus.class,
                                   COMMANDS_RECORDER_DECORATION_ORDER,
                                   (config, name, delegate) -> {
                                       var recording = new RecordingCommandBus(delegate);
                                       recordings.registerCommandBus(recording);
                                       return recording;
                                   });

        if (registry.hasComponent(EventStore.class)) {
            registry.registerDecorator(EventStore.class,
                                       EVENTS_RECORDER_DECORATION_ORDER,
                                       (config, name, delegate) -> {
                                           var recording = new RecordingEventStore(delegate);
                                           recordings.registerEventSink(recording);
                                           return recording;
                                       });
        } else {
            registry.registerDecorator(EventBus.class,
                                       EVENTS_RECORDER_DECORATION_ORDER,
                                       (config, name, delegate) -> {
                                           var recording = new RecordingEventBus(delegate);
                                           recordings.registerEventSink(recording);
                                           return recording;
                                       });
        }
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }
}
