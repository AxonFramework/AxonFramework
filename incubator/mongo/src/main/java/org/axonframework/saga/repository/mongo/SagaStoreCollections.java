package org.axonframework.saga.repository.mongo;

import com.mongodb.DB;
import com.mongodb.DBCollection;

/**
 * <p>Generic template for accessing Mongo for the axon sagas.</p>
 * <p>You can ask for the collection of sagas and association values. We use the mongo client mongo-java-driver. This
 * is a wrapper around the standard mongo methods. For convenience the interface also gives access to the database that
 * contains the axon saga collections.</p>
 * <p>Implementations of this interface must provide the connection to the Mongo database.</p>
 *
 * @author Jettro Coenradie
 */
public interface SagaStoreCollections {
    /**
     * Returns a reference to the collection containing the saga instances.
     *
     * @return DBCollection containing the sagas
     */
    DBCollection sagaCollection();

    /**
     * Returns a reference to the collection containing the association instances.
     *
     * @return DBCollection containing the associations
     */
    DBCollection associationsCollection();

    /**
     * Returns the database for the axon sagas store.
     *
     * @return DB The axon sagas database
     */
    DB database();

}
