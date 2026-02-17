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

package org.axonframework.messaging.core.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * Concurrency policy that requires sequential processing of events raised by the same aggregate. Events from different
 * aggregates may be processed in different threads.
 * <p>
 * This policy only applies for event messages.
 *
 * @param <M> the type of message to sequence
 * @author Allard Buijze
 * @since 0.3.0
 */
public class SequentialPerAggregatePolicy<M extends EventMessage> implements SequencingPolicy<M> {

    private static final SequentialPerAggregatePolicy<? extends EventMessage> INSTANCE = new SequentialPerAggregatePolicy<>();

    /**
     * Return a singleton instance of the {@code SequentialPerAggregatePolicy}.
     *
     * @param <T> the type of message to sequence
     * @return A singleton {@code SequentialPerAggregatePolicy}.
     */
    public static <T extends EventMessage> SequentialPerAggregatePolicy<T> instance() {
        //noinspection unchecked
        return (SequentialPerAggregatePolicy<T>) INSTANCE;
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@Nonnull M message, @Nonnull ProcessingContext context) {
        Objects.requireNonNull(message, "Message may not be null.");
        Objects.requireNonNull(context, "ProcessingContext may not be null.");
        return Optional.ofNullable(context.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY));
    }
}
