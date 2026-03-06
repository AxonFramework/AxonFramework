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

import org.axonframework.common.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * A registry that holds references to the recording components created by
 * {@link MessagesRecordingConfigurationEnhancer}. Decorators store recording instances into this registry, and the
 * {@link AxonTestFixture} reads them to perform assertions.
 * <p>
 * This registry is registered as a regular component in the {@link org.axonframework.common.configuration.Configuration}
 * and can be resolved via {@code configuration.getComponent(RecordingComponentsRegistry.class)}.
 *
 * @author Mateusz Nowak
 * @since 5.0.3
 */
@Internal
public class RecordingComponentsRegistry {

    private RecordingCommandBus commandBus;
    private RecordingEventSink eventSink;

    /**
     * Returns the {@link RecordingCommandBus} that captures command messages dispatched through the
     * {@link org.axonframework.messaging.commandhandling.CommandBus}.
     *
     * @return the recording command bus, or {@code null} if the decorator has not yet run
     */
    @Nullable
    public RecordingCommandBus commandBus() {
        return commandBus;
    }

    /**
     * Returns the {@link RecordingEventSink} that captures event messages published through the
     * {@link org.axonframework.messaging.eventhandling.EventBus} or
     * {@link org.axonframework.eventsourcing.eventstore.EventStore}.
     *
     * @return the recording event sink, or {@code null} if the decorator has not yet run
     */
    @Nullable
    public RecordingEventSink eventSink() {
        return eventSink;
    }

    void registerCommandBus(RecordingCommandBus commandBus) {
        this.commandBus = commandBus;
    }

    void registerEventSink(RecordingEventSink eventSink) {
        this.eventSink = eventSink;
    }
}
