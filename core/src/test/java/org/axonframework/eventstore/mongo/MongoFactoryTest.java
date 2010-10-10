package org.axonframework.eventstore.mongo;

import com.mongodb.Mongo;
import com.mongodb.ServerAddress;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Jettro Coenradie
 */
public class MongoFactoryTest {
    private MongoFactory mongoFactory;

    @Test
    public void checkTestContext_WithSystemParam() {
        mongoFactory = new MongoFactory();
        assertTrue(mongoFactory.isTestContext());

        System.setProperty("axon.mongo.test","false");
        assertFalse(mongoFactory.isTestContext());

        mongoFactory = new MongoFactory(new ArrayList<ServerAddress>());
        assertFalse(mongoFactory.isTestContext());

        System.setProperty("axon.mongo.test","true");
        mongoFactory = new MongoFactory(new ArrayList<ServerAddress>());
        assertTrue(mongoFactory.isTestContext());
    }

    @Test
    public void createMongoInstance() {
        mongoFactory = new MongoFactory();
        Mongo mongoInstance = mongoFactory.createMongoInstance();

        assertNotNull(mongoInstance);
    }
}
