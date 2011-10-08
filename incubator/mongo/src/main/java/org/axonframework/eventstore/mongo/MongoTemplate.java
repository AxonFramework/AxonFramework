package org.axonframework.eventstore.mongo;

import com.mongodb.DB;
import com.mongodb.DBCollection;

/**
 * <p></p>Generic template for accessing Mongo for the axon events.</p>
 *
 * @author Jettro Coenradie
 */
public interface MongoTemplate {
    /**
     * Returns a reference to the collection containing the domain events.
     *
     * @return DBCollection containing the domain events
     */
    DBCollection domainEventCollection();

    /**
     * Returtns a reference to the collection containing the snapshot events.
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
