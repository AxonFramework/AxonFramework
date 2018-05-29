/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

/**
 * Provides a mechanism to adjust the way the EventSourcingRepository reads events
 * from the EventStore.
 * 
 * @author André Bierwolf
 * @since 3.3
 */
@FunctionalInterface
public interface EventStreamLoadingStrategy  {
    
    /**
     * Reads the events for the given aggregateIdentifier from the eventStore. 
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param eventStore          the EventStore to read from
     * @return the domain event stream for the given aggregateIdentifier
     */
    public DomainEventStream readEvents(String aggregateIdentifier, EventStore eventStore);
}
