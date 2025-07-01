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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An {@link EventStore} implementation recording all the events that are
 * {@link #publish(ProcessingContext, List) published}.
 * <p>
 * The recorded events can then be used to assert expectations with test cases.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class RecordingEventStore extends RecordingEventSink implements EventStore {

    /**
     * Creates a new {@link RecordingEventStore} that will record all events published to the given {@code delegate}.
     *
     * @param delegate The {@link EventStore} to which events will be published.
     */
    public RecordingEventStore(EventStore delegate) {
        super(delegate);
    }

    @Override
    public EventStoreTransaction transaction(@NotNull ProcessingContext processingContext) {
        return ((EventStore) super.delegate).transaction(processingContext);
    }

    @Override
    public void describeTo(@NotNull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}
