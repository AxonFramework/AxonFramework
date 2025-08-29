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

package org.axonframework.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;

/**
 * Concurrency policy that requires sequential processing of events raised by the same aggregate. Events from different
 * aggregates may be processed in different threads, as will events that do not extend the DomainEvent type.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class SequentialPerAggregatePolicy implements SequencingPolicy {

    private static final SequentialPerAggregatePolicy INSTANCE = new SequentialPerAggregatePolicy();

    /**
     * Return a singleton instance of the this Sequencing Policy.
     *
     * @return a singleton SequentialPerAggregatePolicy instance
     */
    public static SequentialPerAggregatePolicy instance() {
        return INSTANCE;
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        if (event instanceof DomainEventMessage) {
            var aggregateId = ((DomainEventMessage) event).getAggregateIdentifier();
            return Optional.ofNullable(aggregateId);
        }
        return Optional.empty();
    }
}
