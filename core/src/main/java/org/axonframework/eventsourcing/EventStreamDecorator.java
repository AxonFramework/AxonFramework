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

import java.util.List;
import java.util.stream.Stream;

/**
 * Interface describing a class that can decorates DomainEventStreams when events for aggregates are read or appended.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface EventStreamDecorator {

    /**
     * Called when an event stream is read from the event store.
     * <p/>
     * It is encouraged that decorators do not consume the input stream but only apply intermediate operations. In case
     * a decorator needs to consume the input stream, that decorator is responsible for calling {@link Stream#close()}
     * on the input stream when the output stream is closed.
     *
     * @param aggregateIdentifier The identifier of the aggregate events are loaded for
     * @param eventStream         The eventStream containing the events read from the event store
     * @return the decorated event stream
     */
    DomainEventStream decorateForRead(String aggregateIdentifier, DomainEventStream eventStream);

    /**
     * Called when an event stream is appended to the event store.
     *
     * @param aggregate The aggregate for which the events are being stored
     * @param events    The events to append to the event store
     * @return the decorated event stream
     */
    List<DomainEventMessage<?>> decorateForAppend(Aggregate<?> aggregate, List<DomainEventMessage<?>> events);
}
