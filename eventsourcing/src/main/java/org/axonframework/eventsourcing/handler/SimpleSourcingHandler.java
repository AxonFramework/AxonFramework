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

package org.axonframework.eventsourcing.handler;

import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A simple implementation of {@link SourcingHandler} that reconstructs an entity
 * by sourcing events directly from an {@link EventStore}.
 * <p>
 * This implementation retrieves all events for a given identifier according to the
 * {@link CriteriaResolver} and applies them in order to construct or evolve the entity
 * using the provided {@link InitializingEntityEvolver}.
 * <p>
 * This is a straightforward, non-snapshotting sourcing strategy and is suitable
 * when all events must be applied sequentially from the event store.
 *
 * @param <I> the type of the entity identifier
 * @param <E> the type of the entity
 * @since 5.1.0
 * @author John Hendrikx
 */
@Internal
public class SimpleSourcingHandler<I, E> implements SourcingHandler<I, E> {

    private final EventStore eventStore;
    private final CriteriaResolver<I> criteriaResolver;

    /**
     * Creates a new {@code SimpleSourcingHandler}.
     *
     * @param eventStore the {@link EventStore} from which events are sourced, cannot be {@code null}
     * @param criteriaResolver the resolver to use to create the {@link EventCriteria} for sourcing, cannot be {@code null}
     * @throws NullPointerException when any argument is {@code null}
     */
    public SimpleSourcingHandler(EventStore eventStore, CriteriaResolver<I> criteriaResolver) {
        this.eventStore = Objects.requireNonNull(eventStore, "The eventStore parameter must not be null.");
        this.criteriaResolver = Objects.requireNonNull(criteriaResolver, "The criteriaResolver parameter must not be null.");
    }

    @Override
    public CompletableFuture<E> source(I identifier, InitializingEntityEvolver<I, E> evolver, ProcessingContext pc) {
        EventCriteria criteria = criteriaResolver.resolve(identifier, pc);
        EventStoreTransaction transaction = eventStore.transaction(pc);
        MessageStream<? extends EventMessage> source = transaction.source(SourcingCondition.conditionFor(criteria));

        return source.reduce(
            (E) null,
            (entity, entry) -> evolver.evolve(identifier, entity, entry.message(), pc)
        );
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventStore", eventStore);
        descriptor.describeProperty("criteriaResolver", criteriaResolver);
    }
}
