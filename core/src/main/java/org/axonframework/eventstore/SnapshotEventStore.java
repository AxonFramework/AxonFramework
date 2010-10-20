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

package org.axonframework.eventstore;

import org.axonframework.domain.DomainEvent;

/**
 * Interface describing an event store that is able to store snapshot events. Implementations must also take the stored
 * snapshots into account when loading events. That means that any call to {@link #readEvents(String,
 * org.axonframework.domain.AggregateIdentifier) readEvents(String, AggregateIdentifier)} should return an event stream
 * that starts with the latest suitable snapshot event available in the event store.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface SnapshotEventStore extends EventStore {

    /**
     * Append the given <code>snapshotEvent</code> to the snapshot event log for the given type <code>type</code>. The
     * sequence number of the <code>snapshotEvent</code> must be equal to the sequence number of the last regular domain
     * event that is included in the snapshot.
     * <p/>
     * Note that the aggregate identifier and sequence number must be set on the DomainEvent. See {@link
     * org.axonframework.domain.DomainEvent#DomainEvent(long, org.axonframework.domain.AggregateIdentifier)}.
     *
     * @param type          The type of aggregate the event belongs to
     * @param snapshotEvent The event summarizing one or more domain events for a specific aggregate.
     * @see org.axonframework.domain.DomainEvent#DomainEvent(long, org.axonframework.domain.AggregateIdentifier))
     */
    void appendSnapshotEvent(String type, DomainEvent snapshotEvent);
}
