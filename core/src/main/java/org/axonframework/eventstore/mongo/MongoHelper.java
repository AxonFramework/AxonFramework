package org.axonframework.eventstore.mongo;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;

/**
 * <p>Helper class for interacting with the MongoDB instance containing the axon event store data. You can use the
 * helper to get access to the mongodb database and obtain references to the collection required by axon.</p>
 * <p>By configuring this object, you can change the name of the database as well as the names of the collections that
 * are used</p>
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoHelper {
    private static final String DEFAULT_DOMAINEVENTS_COLLECTION = "domainevents";
    private static final String DEFAULT_SNAPSHOTEVENTS_COLLECTION = "snapshotevents";
    private static final String DEFAULT_AXONFRAMEWORK_DATABASE = "axonframework";

    private Mongo mongoDb;
    private String databaseName = DEFAULT_AXONFRAMEWORK_DATABASE;
    private String domainEventsCollectionName = DEFAULT_DOMAINEVENTS_COLLECTION;
    private String snapshotEventsCollectionName = DEFAULT_SNAPSHOTEVENTS_COLLECTION;

    /**
     * The helper requires an actual <code>Mongo</code> connection provided by the java driver
     * @param mongoDb The actual connection to a MongoDB instance
     */
    public MongoHelper(Mongo mongoDb) {
        this.mongoDb = mongoDb;
    }

    /**
     * Returns a reference to the collection containing the domainevents
     * @return DBCollection containing the domain events
     */
    public DBCollection domainEvents() {
        return database().getCollection(domainEventsCollectionName);
    }

    /**
     * Returtns a reference to the collection containing the snapshot events
     * @return DBCOllection containing the snapshot events
     */
    public DBCollection snapshotEvents() {
        return database().getCollection(snapshotEventsCollectionName);
    }

    /**
     * Returns the database for the axon event store
     * @return The axon event store database
     */
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
     * Changes the name of the collection to store the domain events
     *
     * @param domainEventsCollectionName String containing the name of the collection containing the domain events
     */
    public void setDomainEventsCollectionName(String domainEventsCollectionName) {
        this.domainEventsCollectionName = domainEventsCollectionName;
    }

    /**
     * Changes the name of the collection to store the snapshot events in
     *
     * @param snapshotEventsCollectionName String containing the name of the collection containing the snapshot events
     */
    public void setSnapshotEventsCollectionName(String snapshotEventsCollectionName) {
        this.snapshotEventsCollectionName = snapshotEventsCollectionName;
    }
}
