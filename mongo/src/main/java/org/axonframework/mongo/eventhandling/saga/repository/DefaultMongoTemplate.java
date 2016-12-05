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

package org.axonframework.mongo.eventhandling.saga.repository;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.axonframework.mongo.AbstractMongoTemplate;
import org.bson.Document;

/**
 * MongoTemplate instance providing access to the MongoDB Collection containing stored Sagas.
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultMongoTemplate extends AbstractMongoTemplate implements MongoTemplate {

    private static final String DEFAULT_SAGAS_COLLECTION_NAME = "sagas";

    private final String sagasCollectionName;

    /**
     * Initialize a template for the given {@code mongoDb} instance, using default database name ("axonframework")
     * and collection name ("sagas").
     *
     * @param mongo The Mongo instance providing access to the database
     */
    public DefaultMongoTemplate(MongoClient mongo) {
        super(mongo);
        this.sagasCollectionName = DEFAULT_SAGAS_COLLECTION_NAME;
    }

    /**
     * Creates a template connecting to given {@code mongo} instance, and loads sagas in the collection with given
     * {@code sagasCollectionName}, in a database with given {@code databaseName}.
     *
     * @param mongo               The Mongo instance configured to connect to the Mongo Server
     * @param databaseName        The name of the database containing the data
     * @param sagasCollectionName The collection containing the saga instance
     */
    public DefaultMongoTemplate(MongoClient mongo, String databaseName, String sagasCollectionName) {
        super(mongo, databaseName);
        this.sagasCollectionName = sagasCollectionName;
    }

    @Override
    public MongoCollection<Document> sagaCollection() {
        return database().getCollection(sagasCollectionName);
    }
}
