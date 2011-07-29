/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.BatchHandler;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventStore;

import java.util.List;

/**
 * @author Allard Buijze
 */
public class EventStoreAppender implements BatchHandler<CommandHandlingEntry> {
    private final EventStore eventStore;
    private final String aggregateType;

    public EventStoreAppender(EventStore eventStore, String aggregateType) {
        this.eventStore = eventStore;
        this.aggregateType = aggregateType;
    }

    @Override
    public void onAvailable(CommandHandlingEntry entry) throws Exception {
        List<DomainEvent> events = entry.getEvents();
        eventStore.appendEvents(aggregateType, new SimpleDomainEventStream(events));
    }

    @Override
    public void onEndOfBatch() throws Exception {
    }
}
