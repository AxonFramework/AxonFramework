/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventBus;

/**
 * Provides a mechanism to load events from the underlying event storage.
 * <p/>
 * The EventStore provides access to both the global event stream comprised of all domain and application events, as
 * well as streams containing only events of a single aggregate.
 *
 * @author Rene de Waele
 */
public interface EventStore extends EventBus {

    /**
     * Open an event stream containing all domain events belonging to the given {@code aggregateIdentifier}.
     * <p>
     * The returned stream is <em>finite</em>, ending with the last known event of the aggregate. If the event store
     * holds no events of the given aggregate an empty stream is returned.
     *
     * @param aggregateIdentifier the identifier of the aggregate whose events to fetch
     * @return a stream of all currently stored events of the aggregate
     */
    DomainEventStream readEvents(String aggregateIdentifier);

}
