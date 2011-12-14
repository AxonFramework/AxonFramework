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
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;

/**
 * @author Allard Buijze
 */
public class CommandHandlerInvoker implements EventHandler<CommandHandlingEntry> {

    public static final ThreadLocal<EventSourcedAggregateRoot> preLoadedAggregate = new ThreadLocal<EventSourcedAggregateRoot>();

    @Override
    public void onEvent(CommandHandlingEntry entry, long sequence, boolean endOfBatch) throws Exception {
        final EventSourcedAggregateRoot aggregateRoot = entry.getPreLoadedAggregate();
        preLoadedAggregate.set(entry.getPreLoadedAggregate());
        try {
            Object result = entry.getCommandHandler().handle(entry.getCommand(), null);
            entry.setResult(result);
        } catch (Throwable throwable) {
            entry.setExceptionResult(throwable);
        }

        entry.setEventsToStore(aggregateRoot.getUncommittedEvents());
        entry.setEventsToPublish(aggregateRoot.getUncommittedEvents());
        aggregateRoot.commitEvents();
        preLoadedAggregate.remove();
    }
}
