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
 * Interface describing an instance that can produce a snapshot event based on its current state. Typically, this
 * interface is used on the aggregate root. It can, however, be placed on any component that is able to produce a
 * snapshot event to summarize a number of DomainEvents that occurred in the past.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface SnapshotProducer {

    /**
     * Creates a snapshot event that represents the current state of the implementation. The returned DomainEvent must
     * have the sequence number and aggregate identifier initialized. The sequence number must be equal to the sequence
     * number of the last event that was included as part of the snapshot event. See {@link
     * org.axonframework.domain.DomainEvent#DomainEvent(long, java.util.UUID) DomainEvent(long, UUID)}.
     *
     * @return a snapshot event representing current state.
     */
    DomainEvent createSnapshotEvent();

}
