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

import java.util.List;

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
     * Note that a stream is read-once, similar to InputStream. If you read from the stream, make sure to store the
     * read
     * events and pass them to the chain. Usually, it is best to decorate the given <code>eventStream</code> and pass
     * that to the chain.
     *
     * @param aggregateIdentifier The identifier of the aggregate events are loaded for
     * @param eventStream         The eventStream containing the events to append to the event store
     * @return the decorated event stream
     */
    DomainEventStream decorateForRead(String aggregateIdentifier, DomainEventStream eventStream);

    /**
     * Called when an event stream is appended to the event store.
     *
     * @param aggregate     The aggregate for which the events are being stored
     * @param events        The events to append to the event store
     * @return the decorated event stream
     */
    List<DomainEventMessage<?>> decorateForAppend(Aggregate<?> aggregate,
                                                  List<DomainEventMessage<?>> events);
}
