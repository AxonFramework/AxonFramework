package org.axonframework.saga.repository.mongo;

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
     * and collection name ("sagas).
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