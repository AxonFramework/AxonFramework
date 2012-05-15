package org.axonframework.eventstore.mongo;

import com.mongodb.Mongo;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Jettro Coenradie
 */
public class MongoFactoryTest {

    @Test
    public void createMongoInstance() {
        MongoFactory mongoFactory = new MongoFactory();
        Mongo mongoInstance = mongoFactory.createMongo();

        assertNotNull(mongoInstance);
    }
}
