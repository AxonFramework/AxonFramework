package org.axonframework.eventstore.mongo;

import com.mongodb.MongoOptions;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Jettro Coenradie
 */
public class MongoOptionsFactoryTest {
    private MongoOptionsFactory factory;

    @Before
    public void setUp() throws Exception {
        factory = new MongoOptionsFactory();
    }

    @Test
    public void testCreateMongoOptions_defaults() throws Exception {
        MongoOptions options = factory.createMongoOptions();
        MongoOptions defaults = new MongoOptions();

        assertEquals(defaults.autoConnectRetry,options.autoConnectRetry);
        assertEquals(defaults.maxWaitTime, options.maxWaitTime);
        assertEquals(defaults.socketTimeout, options.socketTimeout);
        assertEquals(defaults.connectionsPerHost, options.connectionsPerHost);
        assertEquals(defaults.connectTimeout, options.connectTimeout);
        assertEquals(defaults.threadsAllowedToBlockForConnectionMultiplier, options.threadsAllowedToBlockForConnectionMultiplier);
    }

    @Test
    public void testCreateMongoOptions_customSet() throws Exception {
        factory.setAutoConnectRetry(true);
        factory.setConnectionsPerHost(9);
        factory.setConnectionTimeout(11);
        factory.setMaxWaitTime(3);
        factory.setSocketTimeOut(23);
        factory.setThreadsAllowedToBlockForConnectionMultiplier(31);

        MongoOptions options = factory.createMongoOptions();
        assertTrue(options.autoConnectRetry);
        assertEquals(3, options.maxWaitTime);
        assertEquals(23, options.socketTimeout);
        assertEquals(9, options.connectionsPerHost);
        assertEquals(11, options.connectTimeout);
        assertEquals(31, options.threadsAllowedToBlockForConnectionMultiplier);

    }
}
