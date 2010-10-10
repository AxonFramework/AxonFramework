package org.axonframework.eventstore.mongo;

import com.mongodb.Mongo;
import com.mongodb.MongoOptions;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Factory bean for a Mongo instance class. This factory is required since we want to support the most basic setup as
 * well as a more advanced setup. The most basic setup makes use of only one instance of Mongo. This scenario is not
 * suitable for a production environment, but it does work for a test environment.</p>
 * <p>The factory supports two environments:</p>
 * <ul>
 * <li>Test - configure this factory with setTestContext(true) or system parameter axon.mongo.test</li>
 * <li>Production - the list of provide <code>ServerAddress</code> instances becomes mandatory.</li>
 * </ul>
 * <p>For production usage we expect at least 1 server to be configured. If not, an <code>IllegalStateException</code>
 * is thrown. Be warned, it is better to provide multiple servers in case of a Replica Set.</p>
 * <p>To configure a Mongo that fits your purpose, you can use two other configuration options. You can provide a
 * WriteConcern and a MongoOptions. Especially the write concern is important for the type of environment. In a test
 * environment, the SAFE is by default used. For a production environment, by default the REPLICA_SAFE is used.</p>
 * <p>Configuring the Mongo instance can be done by providing a <code>MongoOptions</code>. To make this easier, a factory
 * is provided. The <code>MongoOptionsFactory</code> facilitates creating such an object. One good reason to provide
 * a MongoOptions object is to increase the number of connections to the actual mongo database</p>
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoFactory {
    private static final String SYSTEMPROPERTY_TESTCONTEXT = "axon.mongo.test";

    private boolean testContext;
    private List<ServerAddress> mongoAddresses;
    private MongoOptions mongoOptions;
    private WriteConcern writeConcern;

    /**
     * Default constructor that configures the factory to create test context Mongo instances.
     */
    public MongoFactory() {
        this(new ArrayList<ServerAddress>(), new MongoOptions(), WriteConcern.SAFE);
        this.testContext = true;
    }

    /**
     * Constructor creating defaults for WriteConcern.REPLICA_SAFE and default MongoOptions
     *
     * @param mongoAddresses List containing the address of the servers to use
     */
    public MongoFactory(List<ServerAddress> mongoAddresses) {
        this(mongoAddresses, new MongoOptions(), WriteConcern.REPLICAS_SAFE);
    }

    /**
     * Constructor that accepts addresses, options and the default write concern. Used to create a production context
     *
     * @param mongoAddresses List of server addresses to configure the Mongo instance with.
     * @param mongoOptions MongoOptions instance to configure the Mongo instance with
     * @param writeConcern WriteConcern to configure for the connection to the Mongo instance
     */
    public MongoFactory(List<ServerAddress> mongoAddresses, MongoOptions mongoOptions, WriteConcern writeConcern) {
        this.mongoAddresses = mongoAddresses;
        this.mongoOptions = mongoOptions;
        this.writeConcern = writeConcern;
        this.testContext = false;
    }

    /**
     * Provide a list of ServerAddress objects to use for locating the Mongo replica set
     *
     * @param mongoAddresses List of ServerAddress instances
     */
    public void setMongoAddresses(List<ServerAddress> mongoAddresses) {
        this.mongoAddresses = mongoAddresses;
    }

    /**
     * Provide an instance of MongoOptions to be used for the connections
     *
     * @param mongoOptions MongoOptions to overrule the default
     */
    public void setMongoOptions(MongoOptions mongoOptions) {
        this.mongoOptions = mongoOptions;
    }

    /**
     * Sets the testContext, provide true if you want the test context and false if you want the production context
     *
     * @param testContext Boolean indicating the context, true for test and false for production.
     */
    public void setTestContext(boolean testContext) {
        this.testContext = testContext;
    }

    /**
     * Provided a write concern to be used by the mongo instance
     *
     * @param writeConcern WriteConcern to use for the connections
     */
    public void setWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
    }

    public Mongo createMongoInstance() {
        Mongo mongo;
        if (isTestContext()) {
            try {
                mongo = new Mongo(new ServerAddress(),mongoOptions);
            } catch (UnknownHostException e) {
                throw new MongoInitializationException("Could not create the default Mongo instance", e);
            }
        } else {
            if (mongoAddresses.isEmpty()) {
                throw new IllegalStateException("Please configure at least 1 instance of Mongo for production.");
            }
            mongo = new Mongo(mongoAddresses, mongoOptions);
            mongo.setWriteConcern(writeConcern);
        }

        return mongo;
    }

    boolean isTestContext() {
        String systemTestContext = System.getProperty(SYSTEMPROPERTY_TESTCONTEXT);
        if(null != systemTestContext) {
            return Boolean.parseBoolean(systemTestContext);
        } else {
            return testContext;
        }

    }

}
