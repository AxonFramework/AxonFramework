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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

/**
 * Per-fixture {@link EventSink} wrapper that adds the test isolation identifier as metadata to every published
 * {@link EventMessage}. This ensures that events originating from a specific test fixture carry the test's unique
 * identifier, enabling downstream components (tag resolvers, recording filters) to correlate by test scope.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@Internal
class IsolatingEventSink implements EventSink {

    private final EventSink delegate;
    private final String testId;

    /**
     * Constructs a new {@code IsolatingEventSink} that stamps every published event with the given
     * {@code testId}.
     *
     * @param delegate The {@link EventSink} to delegate publishing to after stamping.
     * @param testId   The unique test identifier to attach as metadata.
     */
    IsolatingEventSink(EventSink delegate, String testId) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate EventSink may not be null");
        this.testId = Objects.requireNonNull(testId, "The testId may not be null");
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           List<? extends EventMessage> events) {
        var stamped = events.stream()
                .map(e -> e.andMetadata(Map.of(TEST_ID_METADATA_KEY, testId)))
                .toList();
        return delegate.publish(context, stamped);
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}
