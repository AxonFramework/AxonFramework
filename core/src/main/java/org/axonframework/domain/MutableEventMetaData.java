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

package org.axonframework.domain;

import org.joda.time.DateTime;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of EventMetaData that allows values to be set, as well as read.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class MutableEventMetaData implements Serializable, EventMetaData {

    private static final long serialVersionUID = 6364748679487434079L;
    private static final String IDENTIFIER_KEY = "_identifier";
    private static final String TIMESTAMP_KEY = "_timestamp";

    private final Map<String, Serializable> values = new HashMap<String, Serializable>();

    /**
     * Create a meta-data instance with the given <code>timestamp</code> and <code>eventIdentifier</code> as initial
     * values.
     *
     * @param timestamp       The timestamp of the creation of the event
     * @param eventIdentifier The identifier of the event
     */
    public MutableEventMetaData(DateTime timestamp, String eventIdentifier) {
        values.put(IDENTIFIER_KEY, eventIdentifier);
        values.put(TIMESTAMP_KEY, timestamp);
    }

    /**
     * Create a meta-data instance with the given <code>timestamp</code> and <code>eventIdentifier</code> as initial
     * values.
     *
     * @param timestamp       The timestamp of the creation of the event
     * @param eventIdentifier The identifier of the event
     * @deprecated Hard-coded dependency on UUID type is deprecated for performance reasons. Use {@link
     *             #MutableEventMetaData(org.joda.time.DateTime, String)} instead.
     */
    @Deprecated
    public MutableEventMetaData(DateTime timestamp, UUID eventIdentifier) {
        this(timestamp, eventIdentifier.toString());
    }

    /**
     * Put a key-value pair in the meta data.
     *
     * @param key   The key for which to insert a value
     * @param value The value to insert
     */
    public void put(String key, Serializable value) {
        values.put(key, value);
    }

    @Override
    public DateTime getTimestamp() {
        return (DateTime) values.get(TIMESTAMP_KEY);
    }

    @Override
    public String getEventIdentifier() {
        return values.get(IDENTIFIER_KEY).toString();
    }

    @Override
    public Serializable get(String key) {
        return values.get(key);
    }

    @Override
    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    @Override
    public Set<String> keySet() {
        return values.keySet();
    }
}
