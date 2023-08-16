/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventsourcing.conflictresolution;

import org.axonframework.modelling.command.ConflictingAggregateVersionException;
import org.axonframework.modelling.command.ConflictingModificationException;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

/**
 * Implementation of a {@link ConflictResolver} that fetches any unseen changes from the {@link EventStore}.
 *
 * @author Rene de Waele
 */
public class DefaultConflictResolver implements ConflictResolver {

    private final EventStore eventStore;
    private final String aggregateIdentifier;
    private final long expectedVersion;
    private final long actualVersion;
    private List<DomainEventMessage<?>> events;
    private boolean conflictsResolved;

    /**
     * Initializes a {@link DefaultConflictResolver} using the given {@code eventStore} to load unseen events since
     * given {@code expectedVersion}.
     *
     * @param eventStore          the event store to load unseen events
     * @param aggregateIdentifier the identifier of the event sourced aggregate that is being modified
     * @param expectedVersion     the last seen version (sequence number) of the aggregate
     * @param actualVersion       the actual version (sequence number) of the aggregate
     */
    public DefaultConflictResolver(EventStore eventStore, String aggregateIdentifier, long expectedVersion,
                                   long actualVersion) {
        this.eventStore = eventStore;
        this.aggregateIdentifier = aggregateIdentifier;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    @Override
    public <T extends Exception> void detectConflicts(Predicate<List<DomainEventMessage<?>>> predicate, ContextAwareConflictExceptionSupplier<T> exceptionSupplier) throws T {
        conflictsResolved = true;
        List<DomainEventMessage<?>> unexpectedEvents = unexpectedEvents();
        if (predicate.test(unexpectedEvents)) {
            T exception = exceptionSupplier.supplyException(new DefaultConflictDescription(aggregateIdentifier, expectedVersion,
                                                                                           actualVersion, unexpectedEvents));
            if (exception != null) {
                throw exception;
            }
        }
    }

    /**
     * Ensure that conflicts have been resolved (via ({@link #detectConflicts}). If not, an instance of {@link
     * ConflictingModificationException} (or subtype) should be thrown if there have been any unseen changes.
     */
    public void ensureConflictsResolved() {
        if (!conflictsResolved) {
            throw new ConflictingAggregateVersionException(aggregateIdentifier, expectedVersion, actualVersion);
        }
    }

    private List<DomainEventMessage<?>> unexpectedEvents() {
        if (events == null) {
            if (expectedVersion >= actualVersion) {
                return Collections.emptyList();
            }
            events = eventStore.readEvents(aggregateIdentifier, expectedVersion + 1).asStream()
                               .filter(event -> event.getSequenceNumber() <= actualVersion).collect(toList());
        }
        return events;
    }

}
