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

package org.axonframework.messaging.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.Optional;

/**
 * Concurrency policy that requires sequential processing of events raised by the same aggregate. Events from different
 * aggregates may be processed in different threads.
 *
 * @author Allard Buijze
 * @since 0.3.0
 */
public class SequentialPerAggregatePolicy implements SequencingPolicy {

    private static final SequentialPerAggregatePolicy INSTANCE = new SequentialPerAggregatePolicy();

    /**
     * Return a singleton instance of the {@code SequentialPerAggregatePolicy}.
     *
     * @return A singleton {@code SequentialPerAggregatePolicy}.
     */
    public static SequentialPerAggregatePolicy instance() {
        return INSTANCE;
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        Objects.requireNonNull(event, "EventMessage may not be null.");
        Objects.requireNonNull(context, "ProcessingContext may not be null.");
        return Optional.ofNullable(context.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY));
    }
}
