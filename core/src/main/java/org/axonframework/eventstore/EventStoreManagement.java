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

/**
 * Interface describing operations useful for management purposes. These operations are typically used in migration
 * scripts when deploying new versions of applications.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface EventStoreManagement {

    /**
     * Loads all events available in the event store and calls {@link EventVisitor#doWithEvent(org.axonframework.domain.DomainEvent)}
     * for each event found. Events of a single aggregate are guaranteed to be ordered by their sequence number.
     * <p/>
     * Implementations are encouraged, though not required, to supply events in the absolute chronological order.
     * <p/>
     * Processing stops when the visitor throws an exception.
     *
     * @param visitor The visitor the receives each loaded event
     */
    void visitEvents(EventVisitor visitor);
}
