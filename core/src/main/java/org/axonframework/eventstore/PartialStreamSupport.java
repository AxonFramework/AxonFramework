/*
 * Copyright (c) 2010-2013. Axon Framework
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

import org.axonframework.domain.DomainEventStream;

/**
 * Interface implemented by Event Stores that support reading partial event streams.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public interface PartialStreamSupport {

    /**
     * Returns a Stream containing events for the aggregate identified by the given {@code type} and {@code
     * identifier}, starting at the event with the given {@code firstSequenceNumber} (included).
     * <p/>
     * The returned stream will not contain any snapshot events.
     *
     * @param type                The type identifier of the aggregate
     * @param identifier          The identifier of the aggregate
     * @param firstSequenceNumber The sequence number of the first event to find
     * @return a Stream containing events for the given aggregate, starting at the given first sequence number
     */
    DomainEventStream readEvents(String type, Object identifier, long firstSequenceNumber);

    /**
     * Returns a Stream containing events for the aggregate identified by the given {@code type} and {@code
     * identifier}, starting at the event with the given {@code firstSequenceNumber} (included) up to and including the
     * event with given {@code lastSequenceNumber}.
     * If no event with given {@code lastSequenceNumber} exists, the returned stream will simply read until the end of
     * the aggregate's events.
     * <p/>
     * The returned stream will not contain any snapshot events.
     *
     * @param type                The type identifier of the aggregate
     * @param identifier          The identifier of the aggregate
     * @param firstSequenceNumber The sequence number of the first event to find
     * @param lastSequenceNumber  The sequence number of the last event in the stream
     * @return a Stream containing events for the given aggregate, starting at the given first sequence number
     */
    DomainEventStream readEvents(String type, Object identifier, long firstSequenceNumber, long lastSequenceNumber);
}
