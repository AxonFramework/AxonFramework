/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo.eventsourcing.eventstore;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.axonframework.mongo.AbstractMongoTemplate;
import org.bson.Document;

/**
 * Default implementation for the {@link MongoTemplate}. This implementation requires access to the configured {@link
 * MongoClient} object. You can influence the names of the collections used to store the events as well as the
 * snapshot events.
 *
 * @author Allard Buijze
 * @author Jettro Coenradie
 * @since 2.0 (in incubator since 0.7)
 */
public class DefaultMongoTemplate extends AbstractMongoTemplate implements MongoTemplate {

    private static final String DEFAULT_DOMAINEVENTS_COLLECTION = "domainevents";
    private static final String DEFAULT_SNAPSHOTEVENTS_COLLECTION = "snapshotevents";

    private final String domainEventsCollectionName;
    private final String snapshotEventsCollectionName;

    /**
     * Initializes the MongoTemplate using the given {@code mongo} for database access, using default database
     * and collection names.
     * <p/>
     * Domain events collection name: {@code "domainevents"}<br/>
     * Snapshot events collection name: {@code "snapshotsevents"}
     * <p/>
     * Consider using {@link #DefaultMongoTemplate(com.mongodb.MongoClient, String, String, String)} to
     * provide different names for database and/or collections.
     *
     * @param mongo The actual connection to a MongoDB instance
     */
    public DefaultMongoTemplate(MongoClient mongo) {
        super(mongo);
        domainEventsCollectionName = DEFAULT_DOMAINEVENTS_COLLECTION;
        snapshotEventsCollectionName = DEFAULT_SNAPSHOTEVENTS_COLLECTION;
    }

    /**
     * Creates a template connecting to given {@code mongo} instance, and loads events in the collection with given
     * {@code domainEventsCollectionName} and snapshot events from {@code snapshotEventsCollectionName}, in a
     * database with given {@code databaseName}. When not {@code null}, the given {@code userName} and
     * {@code password} are used to authenticate against the database.
     *
     * @param mongo                        The Mongo instance configured to connect to the Mongo Server
     * @param databaseName                 The name of the database containing the data
     * @param domainEventsCollectionName   The name of the collection containing domain events
     * @param snapshotEventsCollectionName The name of the collection containing snapshot events
     */
    public DefaultMongoTemplate(MongoClient mongo, String databaseName, String domainEventsCollectionName,
            String snapshotEventsCollectionName) {
        super(mongo, databaseName);
        this.domainEventsCollectionName = domainEventsCollectionName;
        this.snapshotEventsCollectionName = snapshotEventsCollectionName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MongoCollection<Document> eventCollection() {
        return database().getCollection(domainEventsCollectionName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MongoCollection<Document> snapshotCollection() {
        return database().getCollection(snapshotEventsCollectionName);
    }
}
