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

package org.axonframework.eventstore.mongo;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;

/**
 * <p>Default implementation for the {@see MongoTemplate}. This implementation requires access to the configured {@see
 * Mongo} object. You can influence the names of the collections used to store the events as well as the
 * snapshot events.</p>
 *
 * @author Allard Buijze
 * @author Jettro Coenradie
 * @since 2.0 (in incubator since 0.7)
 */
public class DefaultMongoTemplate implements MongoTemplate {

    private static final String DEFAULT_DOMAINEVENTS_COLLECTION = "domainevents";
    private static final String DEFAULT_SNAPSHOTEVENTS_COLLECTION = "snapshotevents";
    private static final String DEFAULT_AXONFRAMEWORK_DATABASE = "axonframework";

    private final Mongo mongoDb;
    private final String databaseName;
    private final String domainEventsCollectionName;
    private final String snapshotEventsCollectionName;

    /**
     * Initializes the MongoTemplate using the given <code>mongoDb</code> for database access, using default database
     * and collection names.
     * <p/>
     * Database name: {@value DefaultMongoTemplate#DEFAULT_AXONFRAMEWORK_DATABASE}<br/>
     * Domain events collection name: {@value DefaultMongoTemplate#DEFAULT_DOMAINEVENTS_COLLECTION}<br/>
     * Snapshot events collection name: {@value DefaultMongoTemplate#DEFAULT_SNAPSHOTEVENTS_COLLECTION}
     * <p/>
     * Consider using {@link #DefaultMongoTemplate(com.mongodb.Mongo, String, String, String)} to provide different
     * names for database and/or collections.
     *
     * @param mongoDb The actual connection to a MongoDB instance
     */
    public DefaultMongoTemplate(Mongo mongoDb) {
        this(mongoDb,
             DEFAULT_AXONFRAMEWORK_DATABASE,
             DEFAULT_DOMAINEVENTS_COLLECTION,
             DEFAULT_SNAPSHOTEVENTS_COLLECTION);
    }

    /**
     * Initializes the MongoTemplate using the given <code>mongoDb</code> for database access, and given database and
     * collection names.
     *
     * @param mongoDb                      The Mongo instance providing access to the Mongo database.
     * @param databaseName                 The name of the database that contains the event collections
     * @param domainEventsCollectionName   The name of the collection containing the Domain Events
     * @param snapshotEventsCollectionName The name of the collection containing Snapshot Events
     */
    public DefaultMongoTemplate(Mongo mongoDb, String databaseName, String domainEventsCollectionName,
                                String snapshotEventsCollectionName) {
        this.mongoDb = mongoDb;
        this.databaseName = databaseName;
        this.domainEventsCollectionName = domainEventsCollectionName;
        this.snapshotEventsCollectionName = snapshotEventsCollectionName;
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
}
