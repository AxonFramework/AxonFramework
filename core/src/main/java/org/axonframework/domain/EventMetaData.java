/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.domain;

import org.joda.time.DateTime;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/**
 * Interface towards the meta data properties of an event. Typically, meta-data is information that gives information
 * about the context or origin of an event. Transaction identifiers, the executing user's principal, correlation
 * identifiers are examples of such information.
 * <p/>
 * The meta data of an event will always contain the event identifier and the event creation timestamp.
 * <p/>
 * Note that meta data should be considered immutable from the moment an event is stored in the event store or
 * dispatched on the event bus, whichever comes first.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface EventMetaData {

    /**
     * Returns the time this event was created.
     *
     * @return the time this event was created
     */
    DateTime getTimestamp();

    /**
     * Returns the identifier of this event.
     *
     * @return the identifier of this event
     */
    UUID getEventIdentifier();

    /**
     * Returns the value associated with the given <code>key</code>. Returns <code>null</code> if no such key exists, or
     * if the associated value is <code>null</code>. Use {@link #containsKey(String)} to make this distinction.
     *
     * @param key The key to find the associated value for
     * @return The value assiciated with the given key, or null if it does not exist
     */
    Serializable get(String key);

    /**
     * Indicates whether the given <code>key</code> has been associated with a value (which includes
     * <code>null</code>).
     *
     * @param key The key to check
     * @return <code>true</code> if the key is associated with a value, <code>false</code> otherwise.
     */
    boolean containsKey(String key);

    /**
     * Returns a Set containing the keys registered in the event's meta data.
     *
     * @return a Set containing the keys registered in the event's meta data
     */
    Set<String> keySet();
}
