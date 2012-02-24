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

package org.axonframework.eventstore.mongo;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;

/**
 * <p>Default implementation for the {@see SagaStoreCollections}. This implementation requires access to the configured
 *
 * @author Jettro Coenradie
 * @{see Mongo} object. You can influence the names of the collections used to store the events as well as the
 * snapshot events.</p>
 * @since 0.7
 */
public class DefaultEventStoreCollections implements EventStoreCollections {

    private static final String DEFAULT_DOMAINEVENTS_COLLECTION = "domainevents";
    private static final String DEFAULT_SNAPSHOTEVENTS_COLLECTION = "snapshotevents";
    private static final String DEFAULT_AXONFRAMEWORK_DATABASE = "axonframework";

    private Mongo mongoDb;
    private String databaseName = DEFAULT_AXONFRAMEWORK_DATABASE;
    private String domainEventsCollectionName = DEFAULT_DOMAINEVENTS_COLLECTION;
    private String snapshotEventsCollectionName = DEFAULT_SNAPSHOTEVENTS_COLLECTION;

    /**
     * The helper requires an actual <code>Mongo</code> connection provided by the java driver.
     *
     * @param mongoDb The actual connection to a MongoDB instance
     */
    public DefaultEventStoreCollections(Mongo mongoDb) {
        this.mongoDb = mongoDb;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DBCollection domainEventCollection() {
        return database().getCollection(domainEventsCollectionName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DBCollection snapshotEventCollection() {
        return database().getCollection(snapshotEventsCollectionName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DB database() {
        return mongoDb.getDB(databaseName);
    }

    /**
     * Changes the name of the database where axon events will be stored.
     *
     * @param databaseName String containing the name of the database for axon events
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * Changes the name of the collection to store the domain events.
     *
     * @param domainEventsCollectionName String containing the name of the collection containing the domain events
     */
    public void setDomainEventsCollectionName(String domainEventsCollectionName) {
        this.domainEventsCollectionName = domainEventsCollectionName;
    }

    /**
     * Changes the name of the collection to store the snapshot events in.
     *
     * @param snapshotEventsCollectionName String containing the name of the collection containing the snapshot events
     */
    public void setSnapshotEventsCollectionName(String snapshotEventsCollectionName) {
        this.snapshotEventsCollectionName = snapshotEventsCollectionName;
    }
}
