package org.axonframework.eventstore.mongo;

import com.mongodb.MongoOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Factory class used to create a <code>MongoOptions</code> instance. The instance makes use of the defaults as
 * provided by the MongoOptions class. The moment you set a valid value, that value is used to create the options
 * object</p>
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoOptionsFactory {
    private final static Logger logger = LoggerFactory.getLogger(MongoOptionsFactory.class);

    private MongoOptions defaults;
    private int connectionsPerHost;
    private int connectionTimeout;
    private int maxWaitTime;
    private int threadsAllowedToBlockForConnectionMultiplier;
    private boolean autoConnectRetry;
    private int socketTimeOut;

    public MongoOptionsFactory() {
        defaults = new MongoOptions();
    }

    /**
     * Uses the configured parameters to create a MongoOptions instance
     *
     * @return MongoOptions instance based on the configured properties
     */
    public MongoOptions createMongoOptions() {
        MongoOptions options = new MongoOptions();
        options.connectionsPerHost = getConnectionsPerHost();
        options.connectTimeout = getConnectionTimeout();
        options.maxWaitTime = getMaxWaitTime();
        options.threadsAllowedToBlockForConnectionMultiplier = getThreadsAllowedToBlockForConnectionMultiplier();
        options.autoConnectRetry = isAutoConnectRetry();
        options.socketTimeout = getSocketTimeOut();
        if (logger.isDebugEnabled()) {
            logger.debug("Mongo Options");
            logger.debug("Connections per host :{}",options.connectionsPerHost);
            logger.debug("Connection timeout : {}", options.connectTimeout);
            logger.debug("Max wait timeout : {}", options.maxWaitTime);
            logger.debug("Threads allowed to block : {}", options.threadsAllowedToBlockForConnectionMultiplier);
            logger.debug("Autoconnect retry : {}", options.autoConnectRetry);
            logger.debug("Socket timeout : {}", options.socketTimeout);
        }
        return options;
    }

    public boolean isAutoConnectRetry() {
        return (autoConnectRetry) || defaults.autoConnectRetry;
    }

    public void setAutoConnectRetry(boolean autoConnectRetry) {
        this.autoConnectRetry = autoConnectRetry;
    }

    public int getConnectionsPerHost() {
        return (connectionsPerHost > 0) ? connectionsPerHost : defaults.connectionsPerHost;
    }

    public void setConnectionsPerHost(int connectionsPerHost) {
        this.connectionsPerHost = connectionsPerHost;
    }

    public int getConnectionTimeout() {
        return (connectionTimeout > 0) ? connectionTimeout : defaults.connectTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getMaxWaitTime() {
        return (maxWaitTime > 0) ? maxWaitTime : defaults.maxWaitTime;
    }

    public void setMaxWaitTime(int maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }

    public int getSocketTimeOut() {
        return (socketTimeOut > 0) ? socketTimeOut: defaults.socketTimeout;
    }

    public void setSocketTimeOut(int socketTimeOut) {
        this.socketTimeOut = socketTimeOut;
    }

    public int getThreadsAllowedToBlockForConnectionMultiplier() {
        return (threadsAllowedToBlockForConnectionMultiplier > 0) ? threadsAllowedToBlockForConnectionMultiplier :
                defaults.threadsAllowedToBlockForConnectionMultiplier;
    }

    public void setThreadsAllowedToBlockForConnectionMultiplier(int threadsAllowedToBlockForConnectionMultiplier) {
        this.threadsAllowedToBlockForConnectionMultiplier = threadsAllowedToBlockForConnectionMultiplier;
    }
}
