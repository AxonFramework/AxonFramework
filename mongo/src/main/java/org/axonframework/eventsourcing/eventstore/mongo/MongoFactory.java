/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.mongo;

import com.mongodb.Mongo;
import com.mongodb.MongoOptions;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

/**
 * Convenience class for creating Mongo instances. It helps configuring a Mongo instance with a WriteConcern safe to
 * use in combination with the given server addresses.
 * <p/>
 * Depending on the number of addresses provided, the factory defaults to either {@link WriteConcern#REPLICAS_SAFE}
 * when more than one address is provided, or {@link WriteConcern#FSYNC_SAFE} when only one server is available. The
 * idea of these defaults is that data must be able to survive a (not too heavy) crash without loss of data. We
 * wouldn't want to publish untraceable events, would we...
 *
 * @author Jettro Coenradie
 * @since 2.0 (in incubator since 0.7)
 */
public class MongoFactory {

    private List<ServerAddress> mongoAddresses = Collections.emptyList();
    private MongoOptions mongoOptions = new MongoOptions();
    private WriteConcern writeConcern;

    /**
     * Creates a mongo instance based on the provided configuration. Read javadoc of the class to learn about the
     * configuration options. A new Mongo instance is created each time this method is called.
     *
     * @return a new Mongo instance each time this method is called.
     */
    public Mongo createMongo() {
        Mongo mongo;
        if (mongoAddresses.isEmpty()) {
            try {
                mongo = new Mongo(new ServerAddress(), mongoOptions);
            } catch (UnknownHostException e) {
                throw new IllegalStateException(String.format(
                        "No addresses were provided, but could not find IP for default host: %s",
                        ServerAddress.defaultHost()), e);
            }
        } else {
            mongo = new Mongo(mongoAddresses, mongoOptions);
        }
        mongo.setWriteConcern(defaultWriteConcern());

        return mongo;
    }

    /**
     * Provide a list of ServerAddress objects to use for locating the Mongo replica set. An empty list will result in
     * a single Mongo instance being used on the default host (<code>127.0.0.1</code>) and port
     * (<code>{@value com.mongodb.DBPort#PORT}</code>)
     * <p/>
     * Defaults to an empty list, which locates a single Mongo instance on the default host (<code>127.0.0.1</code>)
     * and port <code>({@value com.mongodb.DBPort#PORT})</code>
     *
     * @param mongoAddresses List of ServerAddress instances
     */
    public void setMongoAddresses(List<ServerAddress> mongoAddresses) {
        this.mongoAddresses = mongoAddresses;
    }

    /**
     * Provide an instance of MongoOptions to be used for the connections. Defaults to a MongoOptions with all its
     * default settings.
     *
     * @param mongoOptions MongoOptions to overrule the default
     */
    public void setMongoOptions(MongoOptions mongoOptions) {
        this.mongoOptions = mongoOptions;
    }

    /**
     * Provided a write concern to be used by the mongo instance. The provided concern should be compatible with the
     * number of addresses provided with {@link #setMongoAddresses(java.util.List)}. For example, providing {@link
     * WriteConcern#REPLICAS_SAFE} in combination with a single address will cause each write operation to hang.
     * <p/>
     * While safe (e.g. {@link WriteConcern#REPLICAS_SAFE}) WriteConcerns allow you to detect concurrency issues
     * immediately, you might want to use a more relaxed write concern if you have other mechanisms in place to ensure
     * consistency.
     * <p/>
     * Defaults to {@link WriteConcern#REPLICAS_SAFE} if you provided more than one address with {@link
     * #setMongoAddresses(java.util.List)}, or {@link WriteConcern#FSYNC_SAFE} if there is only one address (or none at
     * all).
     *
     * @param writeConcern WriteConcern to use for the connections
     */
    public void setWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
    }

    private WriteConcern defaultWriteConcern() {
        if (writeConcern != null) {
            return this.writeConcern;
        } else if (mongoAddresses.size() > 1) {
            return WriteConcern.REPLICAS_SAFE;
        } else {
            return WriteConcern.FSYNC_SAFE;
        }
    }
}
