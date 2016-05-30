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

package org.axonframework.eventhandling.saga.repository.mongo;

import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import org.axonframework.common.mongo.AuthenticatingMongoTemplate;

/**
 * MongoTemplate instance providing access to the MongoDB Collection containing stored Sagas.
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultMongoTemplate extends AuthenticatingMongoTemplate implements MongoTemplate {

    private static final String DEFAULT_SAGAS_COLLECTION_NAME = "sagas";

    private final String sagasCollectionName;

    /**
     * Initialize a template for the given <code>mongoDb</code> instance, using default database name ("axonframework")
     * and collection name ("sagas").
     *
     * @param mongo The Mongo instance providing access to the database
     */
    public DefaultMongoTemplate(Mongo mongo) {
        super(mongo, null, null);
        this.sagasCollectionName = DEFAULT_SAGAS_COLLECTION_NAME;
    }

    /**
     * Creates a template connecting to given <code>mongo</code> instance, and loads sagas in the collection with given
     * <code>sagasCollectionName</code>, in a database with given <code>databaseName</code>. When not
     * <code>null</code>, the given <code>userName</code> and <code>password</code> are used to authenticate against
     * the database.
     *
     * @param mongo               The Mongo instance configured to connect to the Mongo Server
     * @param databaseName        The name of the database containing the data
     * @param sagasCollectionName The collection containing the saga instance
     * @param userName            The username to authenticate with. Use <code>null</code> to skip authentication
     * @param password            The password to authenticate with. Use <code>null</code> to skip authentication
     */
    public DefaultMongoTemplate(Mongo mongo, String databaseName, String sagasCollectionName,
                                String userName, char[] password) {
        super(mongo, databaseName, userName, password);
        this.sagasCollectionName = sagasCollectionName;
    }

    @Override
    public DBCollection sagaCollection() {
        return database().getCollection(sagasCollectionName);
    }
}