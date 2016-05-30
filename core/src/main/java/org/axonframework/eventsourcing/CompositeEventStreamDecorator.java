/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;

import java.util.Collection;
import java.util.List;

/**
 * EventStreamDecorator implementation that delegates to several other decorator instances.
 *
 * @author Allard Buijze
 * @since 2.2.1
 */
public class CompositeEventStreamDecorator implements EventStreamDecorator {

    private final EventStreamDecorator[] eventStreamDecorators;

    /**
     * Initialize the decorator, delegating to the given <code>eventStreamDecorators</code>. The decorators are invoked
     * in the iterator's order on {@link EventStreamDecorator#decorateForRead}, and in reverse order on {@link
     * #decorateForAppend}.
     *
     * @param eventStreamDecorators The decorators to decorate Event Streams with
     */
    public CompositeEventStreamDecorator(Collection<EventStreamDecorator> eventStreamDecorators) {
        this.eventStreamDecorators = eventStreamDecorators
                .toArray(new EventStreamDecorator[eventStreamDecorators.size()]);
    }

    @Override
    public DomainEventStream decorateForRead(String aggregateIdentifier, DomainEventStream eventStream) {
        for (EventStreamDecorator decorator : eventStreamDecorators) {
            eventStream = decorator.decorateForRead(aggregateIdentifier, eventStream);
        }
        return eventStream;
    }

    @Override
    public List<DomainEventMessage<?>> decorateForAppend(Aggregate<?> aggregate,
                                                         List<DomainEventMessage<?>> eventStream) {
        List<DomainEventMessage<?>> events = eventStream;
        for (int i = eventStreamDecorators.length - 1; i >= 0; i--) {
            events = eventStreamDecorators[i].decorateForAppend(aggregate, events);
        }
        return events;
    }
}
