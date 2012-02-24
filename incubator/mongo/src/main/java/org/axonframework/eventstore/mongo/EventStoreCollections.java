package org.axonframework.eventstore.mongo;

import com.mongodb.DB;
import com.mongodb.DBCollection;

/**
 * <p>Generic template for accessing Mongo for the axon events.</p>
 * <p>You can ask for the collection of domain events as well as the collection of snapshot events. We use the mongo
 * client mongo-java-driver. This is a wrapper around the standard mongo methods. For convenience the interface also
 * gives access to the database that contains the axon collections.</p>
 * <p>Implementations of this interface must provide the connection to Mongo.</p>
 *
 * @author Jettro Coenradie
 */
public interface EventStoreCollections {
    /**
     * Returns a reference to the collection containing the domain events.
     *
     * @return DBCollection containing the domain events
     */
    DBCollection domainEventCollection();

    /**
     * Returns a reference to the collection containing the snapshot events.
     *
     * @return DBCollection containing the snapshot events
     */
    DBCollection snapshotEventCollection();

    /**
     * Returns the database for the axon event store.
     *
     * @return DB The axon event store database
     */
    DB database();
}
