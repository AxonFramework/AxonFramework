package org.axonframework.saga.repository.mongo;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;

/**
 * MongoTemplate instance providing access to the MongoDB Collection containing stored Sagas.
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultMongoTemplate implements MongoTemplate {

    private static final String DEFAULT_SAGAS_COLLECTION_NAME = "sagas";
    private static final String DEFAULT_AXONFRAMEWORK_DATABASE = "axonframework";

    private String databaseName = DEFAULT_AXONFRAMEWORK_DATABASE;
    private String sagasCollectionName = DEFAULT_SAGAS_COLLECTION_NAME;

    private Mongo mongoDb;

    /**
     * Initialize a template for the given <code>mongoDb</code> instance.
     *
     * @param mongoDb The Mongo instance providing access to the database
     */
    public DefaultMongoTemplate(Mongo mongoDb) {
        this.mongoDb = mongoDb;
    }

    @Override
    public DBCollection sagaCollection() {
        return database().getCollection(sagasCollectionName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DB database() {
        return mongoDb.getDB(databaseName);
    }

    /**
     * Changes the name of the collection to store the sagas in.
     *
     * @param sagasCollectionName String containing the name of the collection containing the sagas
     */
    public void setSagasCollectionName(String sagasCollectionName) {
        this.sagasCollectionName = sagasCollectionName;
    }

    /**
     * Changes the name of the database where axon events will be stored.
     *
     * @param databaseName String containing the name of the database for axon events
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }
}