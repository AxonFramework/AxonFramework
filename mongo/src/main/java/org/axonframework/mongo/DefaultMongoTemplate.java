/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.mongo;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/**
 * MongoTemplate instance giving direct access to the TrackingToken collection via a given MongoClient instance.
 */
public class DefaultMongoTemplate extends AbstractMongoTemplate implements MongoTemplate {

    private static final String DEFAULT_TRACKINGTOKENS_COLLECTION_NAME = "trackingtokens";
    private static final String DEFAULT_DOMAINEVENTS_COLLECTION = "domainevents";
    private static final String DEFAULT_SNAPSHOTEVENTS_COLLECTION = "snapshotevents";
    private static final String DEFAULT_SAGAS_COLLECTION_NAME = "sagas";

    private final String domainEventsCollectionName;
    private final String snapshotEventsCollectionName;
    private final String trackingTokensCollectionName;
    private final String sagasCollectionName;

    /**
     * Initialize a template for the given {@code mongoDb} instance, using default database name ("axonframework")
     * and collection names ("domainevents", "snapshotevents", "sagas" and "trackingtokens").
     *
     * @param mongo The Mongo instance providing access to the database
     */
    public DefaultMongoTemplate(MongoClient mongo) {
        super(mongo);
        this.trackingTokensCollectionName = DEFAULT_TRACKINGTOKENS_COLLECTION_NAME;
        this.snapshotEventsCollectionName = DEFAULT_SNAPSHOTEVENTS_COLLECTION;
        this.sagasCollectionName = DEFAULT_SAGAS_COLLECTION_NAME;
        this.domainEventsCollectionName = DEFAULT_DOMAINEVENTS_COLLECTION;
    }

    /**
     * Initialize a template for the given {@code mongoDb} instance, using given {@code databaseName} and default
     * collection names ("domainevents", "snapshotevents", "sagas" and "trackingtokens").
     *
     * @param mongo The Mongo instance providing access to the database
     * @param databaseName The name of the database to connect to
     */
    public DefaultMongoTemplate(MongoClient mongo, String databaseName) {
        super(mongo, databaseName);
        this.trackingTokensCollectionName = DEFAULT_TRACKINGTOKENS_COLLECTION_NAME;
        this.snapshotEventsCollectionName = DEFAULT_SNAPSHOTEVENTS_COLLECTION;
        this.sagasCollectionName = DEFAULT_SAGAS_COLLECTION_NAME;
        this.domainEventsCollectionName = DEFAULT_DOMAINEVENTS_COLLECTION;
    }

    private DefaultMongoTemplate(MongoDatabase database,
                                 String domainEventsCollectionName,
                                 String snapshotEventsCollectionName,
                                 String sagasCollectionName,
                                 String trackingTokensCollectionName) {
        super(database);
        this.domainEventsCollectionName = domainEventsCollectionName;
        this.snapshotEventsCollectionName = snapshotEventsCollectionName;
        this.sagasCollectionName = sagasCollectionName;
        this.trackingTokensCollectionName = trackingTokensCollectionName;
    }

    public DefaultMongoTemplate withSnapshotCollection(String snapshotEventsCollectionName) {
        return new DefaultMongoTemplate(database(),
                                        domainEventsCollectionName,
                                        snapshotEventsCollectionName,
                                        sagasCollectionName,
                                        trackingTokensCollectionName);
    }

    public DefaultMongoTemplate withDomainEventsCollection(String domainEventsCollectionName) {
        return new DefaultMongoTemplate(database(),
                                        domainEventsCollectionName,
                                        snapshotEventsCollectionName,
                                        sagasCollectionName,
                                        trackingTokensCollectionName);
    }

    public DefaultMongoTemplate withSagasCollection(String sagasCollectionName) {
        return new DefaultMongoTemplate(database(),
                                        domainEventsCollectionName,
                                        snapshotEventsCollectionName,
                                        sagasCollectionName,
                                        trackingTokensCollectionName);
    }


    public DefaultMongoTemplate withTrackingTokenCollection(String trackingTokensCollectionName) {
        return new DefaultMongoTemplate(database(),
                                        domainEventsCollectionName,
                                        snapshotEventsCollectionName,
                                        sagasCollectionName,
                                        trackingTokensCollectionName);
    }

    @Override
    public MongoCollection<Document> trackingTokensCollection() {
        return database().getCollection(trackingTokensCollectionName);
    }

    @Override
    public MongoCollection<Document> eventCollection() {
        return database().getCollection(domainEventsCollectionName);
    }

    @Override
    public MongoCollection<Document> snapshotCollection() {
        return database().getCollection(snapshotEventsCollectionName);
    }

    @Override
    public MongoCollection<Document> sagaCollection() {
        return database().getCollection(sagasCollectionName);
    }
}
