/*
 * Copyright (c) 2010-2026. Axon Framework
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

import org.jspecify.annotations.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

/**
 * Per-fixture wrapper around a shared {@link RecordingEventSink} that filters {@link #recorded()} results to only
 * include events carrying the matching test isolation identifier.
 * <p>
 * Publishing is delegated to the shared recording sink. Only the read (assertion) side is filtered.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@Internal
class IsolatingRecordingEventSink extends RecordingEventSink {

    private final RecordingEventSink shared;
    private final String testId;

    /**
     * Constructs a new {@code IsolatingRecordingEventSink} that filters recordings from the given {@code shared}
     * recording sink by the given {@code testId}.
     *
     * @param shared The shared {@link RecordingEventSink} that captures all events.
     * @param testId The unique test identifier to filter by.
     */
    IsolatingRecordingEventSink(RecordingEventSink shared, String testId) {
        super(shared);
        this.shared = Objects.requireNonNull(shared, "The shared RecordingEventSink may not be null");
        this.testId = Objects.requireNonNull(testId, "The testId may not be null");
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           List<? extends EventMessage> events) {
        return shared.publish(context, events);
    }

    @Override
    public List<EventMessage> recorded() {
        return shared.recorded().stream()
                .filter(e -> testId.equals(e.metadata().get(TEST_ID_METADATA_KEY)))
                .toList();
    }

    @Override
    public RecordingEventSink reset() {
        shared.reset();
        return this;
    }
}
