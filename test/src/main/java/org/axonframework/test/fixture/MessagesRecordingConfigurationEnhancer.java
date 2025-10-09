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
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventsourcing.eventstore.EventStore;

import java.util.Objects;

/**
 * ConfigurationEnhancer that registers {@link RecordingEventStore}, {@link RecordingEventSink} and
 * {@link RecordingCommandBus}. The recorded messages can then be used to assert expectations with test cases.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MessagesRecordingConfigurationEnhancer implements ConfigurationEnhancer {

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        Objects.requireNonNull(registry, "Cannot enhance a null ComponentRegistry.")
               .registerDecorator(EventStore.class,
                                  Integer.MIN_VALUE,
                                  (config, name, delegate) -> new RecordingEventStore(delegate))
               .registerDecorator(EventBus.class,
                                  Integer.MIN_VALUE + 100,
                                  (config, name, delegate) -> new RecordingEventBus(delegate))
               .registerDecorator(CommandBus.class,
                                  Integer.MAX_VALUE,
                                  (config, name, delegate) -> new RecordingCommandBus(delegate));
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }
}
