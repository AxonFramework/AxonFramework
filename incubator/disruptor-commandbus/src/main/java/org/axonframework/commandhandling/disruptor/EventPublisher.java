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

import com.lmax.disruptor.EventHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventstore.EventStore;

/**
 * @author Allard Buijze
 */
public class EventPublisher implements EventHandler<CommandHandlingEntry> {


    private final EventStore eventStore;
    private final String aggregateType;
    private final EventBus eventBus;

    public EventPublisher(EventStore eventStore, String aggregateType, EventBus eventBus) {
        this.eventStore = eventStore;
        this.aggregateType = aggregateType;
        this.eventBus = eventBus;
    }

    @Override
    public void onEvent(CommandHandlingEntry entry, boolean endOfBatch) throws Exception {
        eventStore.appendEvents(aggregateType, entry.getEventsToStore());
        while (eventBus != null && entry.getEventsToPublish().hasNext()) {
            eventBus.publish(entry.getEventsToPublish().next());
        }
    }
}
