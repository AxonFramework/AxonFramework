/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.mongo;

import com.mongodb.Mongo;
import com.mongodb.MongoOptions;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory bean for a Mongo instance class. This factory is required since we want to support the most basic setup as
 * well as a more advanced setup. The most basic setup makes use of only one instance of Mongo. This scenario is not
 * really suitable for a production environment, but it does work for a simple test environment.
 * <p/>
 * The factory supports two environments: <ul> <li>Single instance - configure this factory with
 * setSingleInstanceContext(true) or system parameter axon.mongo.singleinstance</li> <li>Replica Set - the list of
 * provide <code>ServerAddress</code> instances becomes mandatory.</li> </ul>
 * <p/>
 * For Replica Set usage we expect at least 1 server to be configured. If not, an <code>IllegalStateException</code> is
 * thrown. Be warned, it is better to provide multiple servers in case of a Replica Set.
 * <p/>
 * To configure a Mongo that fits your purpose, you can use two other configuration options. You can provide a
 * WriteConcern and a MongoOptions object.
 * <p/>
 * The write concern is important in relation to the environment. In a single instance context, the SAFE WriteConcern is
 * by default used. For a replica set environment, by default the REPLICA_SAFE is used.
 * <p/>
 * Configuring the Mongo instance thread pool can be done by providing a <code>MongoOptions</code>. To make this easier,
 * a factory is provided. The <code>MongoOptionsFactory</code> facilitates creating such an object. One good reason to
 * provide a MongoOptions object is to increase the number of connections to the actual mongo databasel
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoFactory {

    private static final String SYSTEMPROPERTY_SINGLEINSTANCECONTEXT = "axon.mongo.singleinstance";

    private boolean singleInstanceContext;
    private List<ServerAddress> mongoAddresses;
    private MongoOptions mongoOptions;
    private WriteConcern writeConcern;

    /**
     * Default constructor that configures the factory to create test context Mongo instances.
     */
    public MongoFactory() {
        this(new ArrayList<ServerAddress>(), new MongoOptions(), WriteConcern.SAFE);
        this.singleInstanceContext = true;
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
     * @param mongoOptions   MongoOptions instance to configure the Mongo instance with
     * @param writeConcern   WriteConcern to configure for the connection to the Mongo instance
     */
    public MongoFactory(List<ServerAddress> mongoAddresses, MongoOptions mongoOptions, WriteConcern writeConcern) {
        this.mongoAddresses = mongoAddresses;
        this.mongoOptions = mongoOptions;
        this.writeConcern = writeConcern;
        this.singleInstanceContext = false;
    }

    /**
     * Provide a list of ServerAddress objects to use for locating the Mongo replica set.
     *
     * @param mongoAddresses List of ServerAddress instances
     */
    public void setMongoAddresses(List<ServerAddress> mongoAddresses) {
        this.mongoAddresses = mongoAddresses;
    }

    /**
     * Provide an instance of MongoOptions to be used for the connections.
     *
     * @param mongoOptions MongoOptions to overrule the default
     */
    public void setMongoOptions(MongoOptions mongoOptions) {
        this.mongoOptions = mongoOptions;
    }

    /**
     * Sets the singleInstanceContext, provide true if you want the test context and false if you want the production
     * context.
     *
     * @param testContext Boolean indicating the context, true for test and false for production.
     */
    public void setSingleInstanceContext(boolean testContext) {
        this.singleInstanceContext = testContext;
    }

    /**
     * Provided a write concern to be used by the mongo instance.
     *
     * @param writeConcern WriteConcern to use for the connections
     */
    public void setWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
    }

    /**
     * Creates a mongo instance based on the provided configuration. Read javadoc of the class to learn about the
     * configuration options. A new Mongo instance is created each time this method is called.
     *
     * @return a new Mongo instance each time this method is called.
     */
    public Mongo createMongoInstance() {
        Mongo mongo;
        if (isSingleInstanceContext()) {
            try {
                mongo = new Mongo(new ServerAddress(), mongoOptions);
            } catch (UnknownHostException e) {
                throw new MongoInitializationException("Could not create the default Mongo instance", e);
            }
        } else {
            if (mongoAddresses.isEmpty()) {
                throw new IllegalStateException("Please configure at least 1 instance of Mongo for production.");
            }
            mongo = new Mongo(mongoAddresses, mongoOptions);
        }
        mongo.setWriteConcern(determineWriteConcern());

        return mongo;
    }

    /**
     * Returns whether the factory is used to connect to a single Mongo instance Mongo or to a replica set.
     *
     * @return true if we connect to a single instance Mongo, false for a replica set
     */
    boolean isSingleInstanceContext() {
        String systemTestContext = System.getProperty(SYSTEMPROPERTY_SINGLEINSTANCECONTEXT);
        if (null != systemTestContext) {
            return Boolean.parseBoolean(systemTestContext);
        } else {
            return singleInstanceContext;
        }

    }

    private WriteConcern determineWriteConcern() {
        WriteConcern toUseWriteConcern;
        if (isSingleInstanceContext()) {
            if (this.writeConcern != null) {
                if (this.writeConcern.getW() > WriteConcern.SAFE.getW()) {
                    throw new IllegalArgumentException(
                            "Invalid WriteConcern for a single instance Mongo context, can be maximum SAFE");
                }
                toUseWriteConcern = this.writeConcern;
            } else {
                toUseWriteConcern = WriteConcern.SAFE;
            }
        } else {
            if (this.writeConcern != null) {
                toUseWriteConcern = this.writeConcern;
            } else {
                toUseWriteConcern = WriteConcern.REPLICAS_SAFE;
            }
        }

        return toUseWriteConcern;
    }
}
