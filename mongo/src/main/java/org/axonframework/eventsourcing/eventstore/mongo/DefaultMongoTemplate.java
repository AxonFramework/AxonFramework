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
import com.mongodb.Mongo;
import org.axonframework.common.mongo.AuthenticatingMongoTemplate;

/**
 * Default implementation for the {@link MongoTemplate}. This implementation requires access to the configured {@link
 * Mongo} object. You can influence the names of the collections used to store the events as well as the
 * snapshot events.
 *
 * @author Allard Buijze
 * @author Jettro Coenradie
 * @since 2.0 (in incubator since 0.7)
 */
public class DefaultMongoTemplate extends AuthenticatingMongoTemplate implements MongoTemplate {

    private static final String DEFAULT_DOMAINEVENTS_COLLECTION = "domainevents";
    private static final String DEFAULT_SNAPSHOTEVENTS_COLLECTION = "snapshotevents";

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
     * Consider using {@link #DefaultMongoTemplate(com.mongodb.Mongo, String, String, String, String, char[])} to
     * provide different names for database and/or collections.
     *
     * @param mongo The actual connection to a MongoDB instance
     */
    public DefaultMongoTemplate(Mongo mongo) {
        super(mongo, null, null);
        domainEventsCollectionName = DEFAULT_DOMAINEVENTS_COLLECTION;
        snapshotEventsCollectionName = DEFAULT_SNAPSHOTEVENTS_COLLECTION;
    }

    /**
     * Creates a template connecting to given <code>mongo</code> instance, and loads events in the collection with
     * given <code>domainEventsCollectionName</code> and snapshot events from <code>snapshotEventsCollectionName</code>,
     * in a database with given <code>databaseName</code>. When not <code>null</code>, the given <code>userName</code>
     * and <code>password</code> are used to authenticate against the database.
     *
     * @param mongo                        The Mongo instance configured to connect to the Mongo Server
     * @param databaseName                 The name of the database containing the data
     * @param domainEventsCollectionName   The name of the collection containing domain events
     * @param snapshotEventsCollectionName The name of the collection containing snapshot events
     * @param userName                     The username to authenticate with. Use <code>null</code> to skip
     *                                     authentication
     * @param password                     The password to authenticate with. Use <code>null</code> to skip
     *                                     authentication
     */
    public DefaultMongoTemplate(Mongo mongo, String databaseName, String domainEventsCollectionName,
                                String snapshotEventsCollectionName, String userName, char[] password) {
        super(mongo, databaseName, userName, password);
        this.domainEventsCollectionName = domainEventsCollectionName;
        this.snapshotEventsCollectionName = snapshotEventsCollectionName;
    }

    /**
     * Creates a template connecting to given <code>mongo</code> instance, and loads events in the collection with
     * given <code>domainEventsCollectionName</code> and snapshot events from <code>snapshotEventsCollectionName</code>,
     * in a database with given <code>databaseName</code>. When not <code>null</code>, the given <code>userName</code>
     * and <code>password</code> are used to authenticate against the database.
     *
     * @param mongo                        The Mongo instance configured to connect to the Mongo Server
     * @param databaseName                 The name of the database containing the data
     * @param domainEventsCollectionName   The name of the collection containing domain events
     * @param snapshotEventsCollectionName The name of the collection containing snapshot events
     * @param authenticationDatabase       The name of the database to authenticate to
     * @param userName                     The username to authenticate with. Use <code>null</code> to skip
     *                                     authentication
     * @param password                     The password to authenticate with. Use <code>null</code> to skip
     *                                     authentication
     */
    public DefaultMongoTemplate(Mongo mongo, String databaseName, String domainEventsCollectionName,
            String snapshotEventsCollectionName, String authenticationDatabase, String userName, char[] password) {
        super(mongo, databaseName, authenticationDatabase, userName, password);
        this.domainEventsCollectionName = domainEventsCollectionName;
        this.snapshotEventsCollectionName = snapshotEventsCollectionName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DBCollection eventCollection() {
        return database().getCollection(domainEventsCollectionName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DBCollection snapshotCollection() {
        return database().getCollection(snapshotEventsCollectionName);
    }
}
