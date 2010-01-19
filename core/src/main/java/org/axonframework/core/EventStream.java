/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core;

import java.util.UUID;

/**
 * The EventStream represents a stream of historical events. The order of events in this stream must represent the
 * actual chronological order in which the events happened. An EventStream may provide access to all events (from the
 * first to the most recent) or any subset of these, as long as the stream is continuous.
 * <p/>
 * A stream is continuous if each call to obtain the <code>next</code> stream will return the event that immediately
 * followed the previous one. No events may be <i>skipped</i>.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public interface EventStream {

    /**
     * Returns the aggregate identifier of the aggregate on which these events apply.
     *
     * @return the identifier of the aggregate to which events in this stream apply.
     */
    UUID getAggregateIdentifier();

    /**
     * Returns <code>true</code> if the stream has more events, meaning that a call to <code>next()</code> will not
     * result in an exception. If a call to this method returns <code>false</code>, there is no guarantee about the
     * result of a consecutive call to <code>next()</code>
     *
     * @return <tt>true</tt> if the stream contains more events.
     */
    boolean hasNext();

    /**
     * Returns the next events in the stream, if available. Use <code>hasNext()</code> to obtain a guarantee about the
     * availability of any next event.
     *
     * @return the next event in the stream.
     */
    DomainEvent next();

}
