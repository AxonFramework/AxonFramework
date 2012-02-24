package org.axonframework.mongo;

import com.mongodb.Mongo;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test class for the {@link MongoFactory}, for each test we set the system property to single instance.
 *
 * @author Jettro Coenradie
 */
public class MongoFactoryTest {
    private MongoFactory mongoFactory;

    @Before
    public void setUp() throws Exception {
        System.setProperty("axon.mongo.singleinstance", "true");
    }

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

    @Test(expected = IllegalStateException.class)
    public void createMongoInstance_multipleNoAddresses() {
        System.setProperty("axon.mongo.singleinstance", "false");
        mongoFactory = new MongoFactory();
        mongoFactory.createMongoInstance();
    }

    @Test
    public void createMongoInstance_multipleAddresses() throws UnknownHostException {
        System.setProperty("axon.mongo.singleinstance", "false");
        List<ServerAddress> serverAddresses = new ArrayList<ServerAddress>();
        serverAddresses.add(new ServerAddress("localhost", 27017));
        mongoFactory = new MongoFactory(serverAddresses);
        Mongo mongoInstance = mongoFactory.createMongoInstance();
        assertNotNull(mongoInstance);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createMongoInstance_wrongWriteConcern() {
        mongoFactory = new MongoFactory();
        mongoFactory.setWriteConcern(WriteConcern.REPLICAS_SAFE);
        mongoFactory.createMongoInstance();
    }

    @Test
    public void createMongoInstance_nullWriteConcern() {
        mongoFactory = new MongoFactory();
        mongoFactory.setWriteConcern(null);
        Mongo mongoInstance = mongoFactory.createMongoInstance();
        assertNotNull(mongoInstance);
        assertEquals(WriteConcern.SAFE, mongoInstance.getWriteConcern());
    }

    @Test
    public void createMongoInstance_WriteConcernMultipleInstance() {
        mongoFactory = new MongoFactory();
        Mongo mongoInstance = mongoFactory.createMongoInstance();
        mongoInstance.setWriteConcern(WriteConcern.SAFE);
        assertNotNull(mongoInstance);
        assertEquals(WriteConcern.SAFE, mongoInstance.getWriteConcern());
    }

    @Test
    public void createMongoInstance_nullWriteConcernMultipleInstance() throws UnknownHostException {
        System.setProperty("axon.mongo.singleinstance", "false");
        List<ServerAddress> serverAddresses = new ArrayList<ServerAddress>();
        serverAddresses.add(new ServerAddress("localhost", 27017));
        mongoFactory = new MongoFactory(serverAddresses);
        mongoFactory.setWriteConcern(null);
        Mongo mongoInstance = mongoFactory.createMongoInstance();
        assertNotNull(mongoInstance);
        assertEquals(WriteConcern.REPLICAS_SAFE, mongoInstance.getWriteConcern());
    }

}
