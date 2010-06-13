/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEvent;

/**
 * Snapshot event that captures the entire aggregate. The motivation is that the aggregate contains all relevant state.
 *
 * @author Allard Buijze
 * @param <T> The type of aggregate this snapshot captures
 * @since 0.6
 */
public class AggregateSnapshot<T extends EventSourcedAggregateRoot> extends DomainEvent {

    private static final long serialVersionUID = -4553033098609759034L;

    private final T aggregate;

    /**
     * Initialize a new AggregateSnapshot for the given <code>aggregate</code>. Note that the aggregate may not contain
     * uncommitted modifications.
     *
     * @param aggregate The aggregate containing the state to capture in the snapshot
     * @throws IllegalArgumentException if the aggregate contains uncommitted modifications
     */
    public AggregateSnapshot(T aggregate) {
        super(aggregate.getLastCommittedEventSequenceNumber(), aggregate.getIdentifier());
        if (aggregate.getUncommittedEventCount() != 0) {
            throw new IllegalArgumentException("Aggregate may not have uncommitted modifications");
        }
        this.aggregate = aggregate;
    }

    /**
     * Return the aggregate that was captured in this snapshot.
     *
     * @return the aggregate that was captured in this snapshot.
     */
    public T getAggregate() {
        return aggregate;
    }
}
