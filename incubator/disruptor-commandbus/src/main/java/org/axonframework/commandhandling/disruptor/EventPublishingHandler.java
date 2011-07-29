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
import org.axonframework.eventhandling.EventBus;

/**
 * @author Allard Buijze
 */
public class EventPublishingHandler implements BatchHandler<CommandHandlingEntry> {

    private final EventBus eventBus;

    public EventPublishingHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void onAvailable(CommandHandlingEntry entry) throws Exception {
        for (DomainEvent event : entry.getEvents()) {
            eventBus.publish(event);
        }
    }

    @Override
    public void onEndOfBatch() throws Exception {
    }
}
