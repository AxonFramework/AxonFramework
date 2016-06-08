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

package org.axonframework.eventsourcing.eventstore.mongo;

import com.mongodb.DBCollection;

/**
 * Interface describing a mechanism that provides access to the Database and Collections required by the
 * MongoEventStore.
 *
 * @author Jettro Coenradie
 * @since 2.0 (in incubator since 0.7)
 */
public interface MongoTemplate {

    /**
     * Returns a reference to the collection containing the events.
     *
     * @return DBCollection containing the domain events
     */
    DBCollection eventCollection();

    /**
     * Returns a reference to the collection containing the snapshot events.
     *
     * @return DBCollection containing the snapshot events
     */
    DBCollection snapshotCollection();
}
