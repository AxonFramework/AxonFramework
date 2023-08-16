/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.mongo.eventsourcing.eventstore;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Class copied from Axon 3 to be able to restore Axon 3 Tokens from Axon 4 applications.
 *
 * @deprecated this class is available for backward compatibility with instances that were serialized with Axon 3. Use
 * {@link org.axonframework.extensions.mongo.eventsourcing.eventstore.MongoTrackingToken} instead.
 */
@Deprecated
public class MongoTrackingToken implements Serializable {

    private static final long serialVersionUID = 8211720263575974485L;

    private long timestamp;
    private Map<String, Long> trackedEvents;

    /**
     * Get the timestamp of the last event tracked by this token.
     *
     * @return the timestamp of the event with this token
     */
    public Instant getTimestamp() {
        return Instant.ofEpochMilli(timestamp);
    }

    /**
     * Gets tracked events. The key is the event identifier, and the value is the timestamp.
     *
     * @return tracked events
     */
    public Map<String, Long> getTrackedEvents() {
        return Collections.unmodifiableMap(trackedEvents);
    }


    private Object readResolve() {
        return org.axonframework.extensions.mongo.eventsourcing.eventstore.MongoTrackingToken.of(
                getTimestamp(), trackedEvents
        );
    }
}
