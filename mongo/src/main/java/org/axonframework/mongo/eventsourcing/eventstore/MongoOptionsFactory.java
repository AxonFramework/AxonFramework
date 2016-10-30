/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo.eventsourcing.eventstore;

import com.mongodb.MongoClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Factory class used to create a {@code MongoOptions} instance. The instance makes use of the defaults as provided
 * by the MongoOptions class. The moment you set a valid value, that value is used to create the options object.
 * </p>
 *
 * @author Jettro Coenradie
 * @since 2.0 (in incubator since 0.7)
 */
public class MongoOptionsFactory {

    private static final Logger logger = LoggerFactory.getLogger(MongoOptionsFactory.class);

    private final MongoClientOptions defaults;
    private int connectionsPerHost;
    private int connectionTimeout;
    private int maxWaitTime;
    private int threadsAllowedToBlockForConnectionMultiplier;
    private int socketTimeOut;

    /**
     * Default constructor for the factory that initializes the defaults.
     */
    public MongoOptionsFactory() {
        defaults = MongoClientOptions.builder().build();
    }

    /**
     * Uses the configured parameters to create a MongoOptions instance.
     *
     * @return MongoOptions instance based on the configured properties
     */
    public MongoClientOptions createMongoOptions() {
        MongoClientOptions options = MongoClientOptions.builder()
                .connectionsPerHost(getConnectionsPerHost())
                .connectTimeout(getConnectionTimeout())
                .maxWaitTime(getMaxWaitTime())
                .threadsAllowedToBlockForConnectionMultiplier(getThreadsAllowedToBlockForConnectionMultiplier())
                .socketTimeout(getSocketTimeOut()).build();
        if (logger.isDebugEnabled()) {
            logger.debug("Mongo Options");
            logger.debug("Connections per host :{}", options.getConnectionsPerHost());
            logger.debug("Connection timeout : {}", options.getConnectTimeout());
            logger.debug("Max wait timeout : {}", options.getMaxWaitTime());
            logger.debug("Threads allowed to block : {}", options.getThreadsAllowedToBlockForConnectionMultiplier());
            logger.debug("Socket timeout : {}", options.getSocketTimeout());
        }
        return options;
    }

    /**
     * Getter for connectionsPerHost.
     *
     * @return number representing the connections per host
     */
    public int getConnectionsPerHost() {
        return (connectionsPerHost > 0) ? connectionsPerHost : defaults.getConnectionsPerHost();
    }

    /**
     * Setter for the connections per host that are allowed.
     *
     * @param connectionsPerHost number representing the number of connections per host
     */
    public void setConnectionsPerHost(int connectionsPerHost) {
        this.connectionsPerHost = connectionsPerHost;
    }

    /**
     * Connection time out in milli seconds for doing something in mongo. Zero is indefinite
     *
     * @return number representing milli seconds of timeout
     */
    public int getConnectionTimeout() {
        return (connectionTimeout > 0) ? connectionTimeout : defaults.getConnectTimeout();
    }

    /**
     * Setter for the connection time out.
     *
     * @param connectionTimeout number representing the connection timeout in millis
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * get the maximum time a blocked thread that waits for a connection should wait.
     *
     * @return number of milli seconds the thread waits for a connection
     */
    public int getMaxWaitTime() {
        return (maxWaitTime > 0) ? maxWaitTime : defaults.getMaxWaitTime();
    }

    /**
     * Set the max wait time for a blocked thread in milli seconds.
     *
     * @param maxWaitTime number representing the number of milli seconds to wait for a thread
     */
    public void setMaxWaitTime(int maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }

    /**
     * Getter for the socket timeout.
     *
     * @return Number representing the amount of milli seconds to wait for a socket connection
     */
    public int getSocketTimeOut() {
        return (socketTimeOut > 0) ? socketTimeOut : defaults.getSocketTimeout();
    }

    /**
     * Setter for the socket time out.
     *
     * @param socketTimeOut number representing the amount of milli seconds to wait for a socket connection
     */
    public void setSocketTimeOut(int socketTimeOut) {
        this.socketTimeOut = socketTimeOut;
    }

    /**
     * Getter for the amount of threads that block in relation to the amount of possible connections.
     *
     * @return Number representing the multiplier of maximum allowed blocked connections in relation to the maximum
     *         allowed connections
     */
    public int getThreadsAllowedToBlockForConnectionMultiplier() {
        return (threadsAllowedToBlockForConnectionMultiplier > 0)
                ? threadsAllowedToBlockForConnectionMultiplier
                : defaults.getThreadsAllowedToBlockForConnectionMultiplier();
    }

    /**
     * Set the multiplier for the amount of threads to block in relation to the maximum amount of connections.
     *
     * @param threadsAllowedToBlockForConnectionMultiplier
     *            Number representing the multiplier of the amount of threads to block in relation to the connections
     *            that are allowed.
     */
    public void setThreadsAllowedToBlockForConnectionMultiplier(int threadsAllowedToBlockForConnectionMultiplier) {
        this.threadsAllowedToBlockForConnectionMultiplier = threadsAllowedToBlockForConnectionMultiplier;
    }
}
