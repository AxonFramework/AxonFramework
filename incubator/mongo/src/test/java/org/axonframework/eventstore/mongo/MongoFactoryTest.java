package org.axonframework.eventstore.mongo;

import com.mongodb.Mongo;
import com.mongodb.ServerAddress;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * @author Jettro Coenradie
 */
public class MongoFactoryTest {
    private MongoFactory mongoFactory;

    @Test
    public void checkTestContext_WithSystemParam() {
        mongoFactory = new MongoFactory();
        assertTrue(mongoFactory.isSingleInstanceContext());

        System.setProperty("axon.mongo.singleinstance", "false");
        assertFalse(mongoFactory.isSingleInstanceContext());

        mongoFactory = new MongoFactory(new ArrayList<ServerAddress>());
        assertFalse(mongoFactory.isSingleInstanceContext());

        System.setProperty("axon.mongo.singleinstance", "true");
        mongoFactory = new MongoFactory(new ArrayList<ServerAddress>());
        assertTrue(mongoFactory.isSingleInstanceContext());
    }

    @Test
    public void createMongoInstance() {
        mongoFactory = new MongoFactory();
        Mongo mongoInstance = mongoFactory.createMongoInstance();

        assertNotNull(mongoInstance);
    }
}
