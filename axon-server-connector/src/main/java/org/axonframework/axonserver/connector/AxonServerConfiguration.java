/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.control.NodeInfo;
import org.axonframework.axonserver.connector.event.util.EventCipher;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Configuration class provided configurable fields and defaults for anything Axon Server related.
 *
 * @author Marc Gathier
 * @since 4.0
 */
@ConfigurationProperties(prefix = "axon.axonserver")
public class AxonServerConfiguration {

    private static final int DEFAULT_GRPC_PORT = 8124;
    private static final String DEFAULT_SERVERS = "localhost";
    private static final String DEFAULT_CONTEXT = "default";

    /**
     * Whether (automatic) configuration of the AxonServer Connector is enabled. When {@code false}, the connector will
     * not be implicitly be configured. Defaults to {@code true}.
     * <p>
     * Note that this setting will only affect automatic configuration by Application Containers (such as Spring).
     */
    private boolean enabled = true;

    /**
     * Comma separated list of AxonServer servers. Each element is hostname or hostname:grpcPort. When no grpcPort is
     * specified, default port 8124 is used.
     */
    private String servers = DEFAULT_SERVERS;

    /**
     * Client identifier as it registers itself to AxonServer, must be unique.
     */
    private String clientId = ManagementFactory.getRuntimeMXBean().getName();

    /**
     * Application name, defaults to spring.application.name multiple instances of the same application share the same
     * application name, but each must have a different clientId.
     */
    private String componentName;

    /**
     * Token for access control
     */
    private String token;

    /**
     * Bounded context that this application operates in. Defaults to {@code "default"}.
     */
    private String context = DEFAULT_CONTEXT;

    /**
     * Certificate file for SSL.
     */
    private String certFile;

    /**
     * Use TLS for connection to AxonServer.
     */
    private boolean sslEnabled;

    /**
     * Initial number of permits send for message streams (events, commands, queries).
     */
    private Integer initialNrOfPermits = 5000;

    /**
     * Additional number of permits send for message streams (events, commands, queries) when application is ready for
     * more messages.
     * <p>
     * A value of {@code null}, 0, and negative values will have the client request the number of permits required to
     * get from the "new-permits-threshold" to "initial-nr-of-permits".
     */
    private Integer nrOfNewPermits = null;

    /**
     * Threshold at which application sends new permits to server.
     * <p>
     * A value of {@code null}, 0, and negative values will have the threshold set to 50% of "initial-nr-of-permits".
     */
    private Integer newPermitsThreshold = null;

    /**
     * Specific flow control settings for the event message stream.
     * <p>
     * When not specified (null) the default flow control properties initialNrOfPermits, nrOfNewPermits en
     * newPermitsThreshold will be used.
     */
    private FlowControlConfiguration eventFlowControl;

    /**
     * Specific flow control settings for the queue message stream.
     */
    private FlowControlConfiguration queryFlowControl;

    /**
     * Specific flow control settings for the command message stream.
     */
    private FlowControlConfiguration commandFlowControl;

    /**
     * Number of threads executing commands.
     */
    private int commandThreads = 10;

    /**
     * Number of threads executing queries.
     */
    private int queryThreads = 10;

    /**
     * Interval (in ms.) application sends status updates on event processors to AxonServer.
     */
    private int processorsNotificationRate = 500;

    /**
     * Initial delay (in ms.) before application sends first status update on event processors to AxonServer.
     */
    private int processorsNotificationInitialDelay = 5000;

    /**
     * An {@link EventCipher} which is used to encrypt and decrypt events and snapshots. Defaults to
     * {@link EventCipher#EventCipher()}.
     *
     * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
     * connector</a>
     */
    @Deprecated
    private EventCipher eventCipher = new EventCipher();

    /**
     * Timeout (in ms) for keep alive requests.
     */
    private long keepAliveTimeout = 5000;

    /**
     * Interval (in ms) for keep alive requests, 0 is keep-alive disabled. Defaults to {@code 1000}.
     */
    private long keepAliveTime = 1_000;

    /**
     * An {@code int} indicating the maximum number of Aggregate snapshots which will be retrieved. Defaults to
     * {@code 1}.
     */
    private int snapshotPrefetch = 1;

    /**
     * Indicates whether the download advice message should be suppressed, even when default connection properties
     * (which are generally only used in DEV mode) are used. Defaults to false.
     */
    private boolean suppressDownloadMessage = false;

    /**
     * GRPC max inbound message size, 0 keeps default value.
     */
    private int maxMessageSize = 0;

    /**
     * Timeout (in milliseconds) to wait for response on commit.
     */
    private int commitTimeout = 10000;

    /**
     * Flag that allows blacklisting of Event types to be disabled. Disabling this may have serious performance impact,
     * as it requires all messages from AxonServer to be sent to clients, even if a Client is unable to process the
     * message.
     * <p>
     * Default is to have blacklisting enabled.
     */
    private boolean disableEventBlacklisting = false;

    /**
     * The number of messages that may be in-transit on the network/grpc level when streaming data from the server.
     * Setting this to 0 (or a negative value) will disable buffering, and requires each message sent by the server to
     * be acknowledged before the next message may be sent. Defaults to 500.
     */
    private int maxGrpcBufferedMessages = 500;

    /**
     * It represents the fixed value of load factor sent to Axon Server for any command's subscription if no specific
     * implementation of CommandLoadFactorProvider is configured. The default value is 100.
     */
    private int commandLoadFactor = 100;

    /**
     * Represents the maximum time in milliseconds a request for the initial Axon Server connection may last. Defaults
     * to 5000 (5 seconds).
     */
    private long connectTimeout = 5000;

    /**
     * Indicates whether it is OK to query events from the local Axon Server node - the node the client is currently
     * connected to. This means that the client will probably get stale events since all events my not be replicated to
     * this node yet. Can be used when the criteria for eventual consistency is less strict. It will spread the load for
     * querying events - not all requests will go to the leader of the cluster anymore.
     * <p>
     * If Axon Server SE is used, this property has no effect.
     * </p>
     */
    private boolean forceReadFromLeader = false;

    /**
     * Configuration specifics on sending heartbeat messages to ensure a fully operational end-to-end connection with
     * Axon Server.
     */
    private HeartbeatConfiguration heartbeat = new HeartbeatConfiguration();

    /**
     * Properties describing the settings for {@link org.axonframework.eventhandling.EventProcessor EventProcessors}.
     */
    private EventProcessorConfiguration eventProcessorConfiguration = new EventProcessorConfiguration();

    /**
     * Properties describing the settings for
     * {@link org.axonframework.axonserver.connector.event.axon.AxonServerEventStore EventStore}.
     */
    private EventStoreConfiguration eventStoreConfiguration = new EventStoreConfiguration();

    /**
     * Instantiate a {@link Builder} to create an {@link AxonServerConfiguration}.
     *
     * @return a {@link Builder} to be able to create an {@link AxonServerConfiguration}.
     */
    public static Builder builder() {
        Builder builder = new Builder();
        if (Boolean.getBoolean("axon.axonserver.suppressDownloadMessage")) {
            builder.suppressDownloadMessage();
        }

        return builder;
    }

    /**
     * Instantiate a default {@link AxonServerConfiguration}.
     */
    public AxonServerConfiguration() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServers() {
        return servers;
    }

    public void setServers(String routingServers) {
        this.servers = routingServers;
        suppressDownloadMessage = true;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getComponentName() {
        return componentName == null
                ? System.getProperty("axon.application.name", "Unnamed-" + clientId)
                : componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCertFile() {
        return certFile;
    }

    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public Integer getInitialNrOfPermits() {
        return initialNrOfPermits;
    }

    public void setInitialNrOfPermits(Integer initialNrOfPermits) {
        this.initialNrOfPermits = initialNrOfPermits;
    }

    public Integer getNrOfNewPermits() {
        if (nrOfNewPermits == null || nrOfNewPermits <= 0) {
            return getInitialNrOfPermits() - getNewPermitsThreshold();
        }
        return nrOfNewPermits;
    }

    public void setNrOfNewPermits(Integer nrOfNewPermits) {
        this.nrOfNewPermits = nrOfNewPermits;
    }

    public Integer getNewPermitsThreshold() {
        if (newPermitsThreshold == null || newPermitsThreshold <= 0) {
            return initialNrOfPermits / 2;
        }
        return newPermitsThreshold;
    }

    public void setNewPermitsThreshold(Integer newPermitsThreshold) {
        this.newPermitsThreshold = newPermitsThreshold;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public List<NodeInfo> routingServers() {
        String[] serverArr = servers.split(",");
        return Arrays.stream(serverArr).map(server -> {
            String[] s = server.trim().split(":");
            if (s.length > 1) {
                return NodeInfo.newBuilder().setHostName(s[0]).setGrpcPort(Integer.parseInt(s[1])).build();
            }
            return NodeInfo.newBuilder().setHostName(s[0]).setGrpcPort(DEFAULT_GRPC_PORT).build();
        }).collect(Collectors.toList());
    }

    public EventCipher getEventCipher() {
        return eventCipher;
    }

    private void setEventSecretKey(String key) {
        if (key != null && key.length() > 0) {
            eventCipher = new EventCipher(key.getBytes(StandardCharsets.US_ASCII));
        }
    }

    public Integer getCommandThreads() {
        return commandThreads;
    }

    public void setCommandThreads(Integer commandThreads) {
        this.commandThreads = commandThreads;
    }

    public void setCommandThreads(int commandThreads) {
        this.commandThreads = commandThreads;
    }

    public int getQueryThreads() {
        return queryThreads;
    }

    public void setQueryThreads(int queryThreads) {
        this.queryThreads = queryThreads;
    }

    public int getProcessorsNotificationRate() {
        return processorsNotificationRate;
    }

    public void setProcessorsNotificationRate(int processorsNotificationRate) {
        this.processorsNotificationRate = processorsNotificationRate;
    }

    public int getProcessorsNotificationInitialDelay() {
        return processorsNotificationInitialDelay;
    }

    public void setProcessorsNotificationInitialDelay(int processorsNotificationInitialDelay) {
        this.processorsNotificationInitialDelay = processorsNotificationInitialDelay;
    }

    public long getKeepAliveTimeout() {
        return this.keepAliveTimeout;
    }

    public void setKeepAliveTimeout(long keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public boolean getSuppressDownloadMessage() {
        return suppressDownloadMessage;
    }

    public void setSuppressDownloadMessage(boolean suppressDownloadMessage) {
        this.suppressDownloadMessage = suppressDownloadMessage;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public int getSnapshotPrefetch() {
        return snapshotPrefetch;
    }

    public void setSnapshotPrefetch(int snapshotPrefetch) {
        this.snapshotPrefetch = snapshotPrefetch;
    }

    public boolean isDisableEventBlacklisting() {
        return disableEventBlacklisting;
    }

    public void setDisableEventBlacklisting(boolean disableEventBlacklisting) {
        this.disableEventBlacklisting = disableEventBlacklisting;
    }

    public int getCommitTimeout() {
        return commitTimeout;
    }

    public void setCommitTimeout(int commitTimeout) {
        this.commitTimeout = commitTimeout;
    }

    public int getMaxGrpcBufferedMessages() {
        return maxGrpcBufferedMessages;
    }

    public void setMaxGrpcBufferedMessages(int maxGrpcBufferedMessages) {
        this.maxGrpcBufferedMessages = maxGrpcBufferedMessages;
    }

    public int getCommandLoadFactor() {
        return commandLoadFactor;
    }

    public void setCommandLoadFactor(int commandLoadFactor) {
        this.commandLoadFactor = commandLoadFactor;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public boolean isForceReadFromLeader() {
        return forceReadFromLeader;
    }

    public void setForceReadFromLeader(boolean forceReadFromLeader) {
        this.forceReadFromLeader = forceReadFromLeader;
    }

    public FlowControlConfiguration getEventFlowControl() {
        if (eventFlowControl == null) {
            return new FlowControlConfiguration(getInitialNrOfPermits(), getNrOfNewPermits(), getNewPermitsThreshold());
        }
        return eventFlowControl;
    }

    public void setEventFlowControl(FlowControlConfiguration eventFlowControl) {
        this.eventFlowControl = eventFlowControl;
    }

    public FlowControlConfiguration getQueryFlowControl() {
        if (queryFlowControl == null) {
            return new FlowControlConfiguration(getInitialNrOfPermits(), getNrOfNewPermits(), getNewPermitsThreshold());
        }
        return queryFlowControl;
    }

    public void setQueryFlowControl(FlowControlConfiguration queryFlowControl) {
        this.queryFlowControl = queryFlowControl;
    }

    public FlowControlConfiguration getCommandFlowControl() {
        if (commandFlowControl == null) {
            return new FlowControlConfiguration(getInitialNrOfPermits(), getNrOfNewPermits(), getNewPermitsThreshold());
        }
        return commandFlowControl;
    }

    public void setCommandFlowControl(FlowControlConfiguration commandFlowControl) {
        this.commandFlowControl = commandFlowControl;
    }

    public FlowControlConfiguration getDefaultFlowControlConfiguration() {
        return new FlowControlConfiguration(initialNrOfPermits, nrOfNewPermits, newPermitsThreshold);
    }

    public HeartbeatConfiguration getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(HeartbeatConfiguration heartbeat) {
        this.heartbeat = heartbeat;
    }

    /**
     * Return the configured {@link EventProcessorConfiguration} of this application for Axon Server.
     *
     * @return The configured {@link EventProcessorConfiguration} of this application for Axon Server.
     */
    @ConfigurationProperties(prefix = "axon.axonserver.eventhandling")
    public EventProcessorConfiguration getEventProcessorConfiguration() {
        return eventProcessorConfiguration;
    }

    /**
     * Set the {@link EventProcessorConfiguration} of this application for Axon Server
     *
     * @param eventProcessorConfiguration The {@link EventProcessorConfiguration} to set for this application.
     */
    public void setEventProcessorConfiguration(EventProcessorConfiguration eventProcessorConfiguration) {
        this.eventProcessorConfiguration = eventProcessorConfiguration;
    }

    /**
     * Return the configured {@link EventStoreConfiguration} of this application for Axon Server.
     *
     * @return The configured {@link EventStoreConfiguration} of this application for Axon Server.
     */
    @ConfigurationProperties(prefix = "axon.axonserver.event-store")
    public EventStoreConfiguration getEventStoreConfiguration() {
        return eventStoreConfiguration;
    }

    /**
     * Set the {@link EventStoreConfiguration} of this application for Axon Server
     *
     * @param eventStoreConfiguration The {@link EventStoreConfiguration} to set for this application.
     */
    public void setEventStoreConfiguration(EventStoreConfiguration eventStoreConfiguration) {
        this.eventStoreConfiguration = eventStoreConfiguration;
    }

    /**
     * Configuration class for Flow Control of specific message types.
     *
     * @author Gerlo Hesselink
     * @since 4.3
     */
    public static class FlowControlConfiguration {

        /**
         * Initial number of permits send for message streams (events, commands, queries).
         */
        private Integer initialNrOfPermits = 5000;

        /**
         * Additional number of permits send for message streams (events, commands, queries) when application is ready
         * for more messages.
         * <p>
         * A value of {@code null}, 0, and negative values will have the client request the number of permits required
         * to get from the "new-permits-threshold" to "initial-nr-of-permits".
         */
        private Integer nrOfNewPermits = null;

        /**
         * Threshold at which application sends new permits to server.
         * <p>
         * A value of {@code null}, 0, and negative values will have the threshold set to 50% of
         * "initial-nr-of-permits".
         */
        private Integer newPermitsThreshold = null;

        public FlowControlConfiguration() {
        }

        /**
         * Construct a {@link FlowControlConfiguration}.
         *
         * @param initialNrOfPermits  initial nr of new permits
         * @param nrOfNewPermits      additional number of permits when application is ready for message
         * @param newPermitsThreshold threshold at which application sends new permits to server
         */
        public FlowControlConfiguration(Integer initialNrOfPermits, Integer nrOfNewPermits,
                                        Integer newPermitsThreshold) {
            this.initialNrOfPermits = initialNrOfPermits;
            this.nrOfNewPermits = nrOfNewPermits;
            this.newPermitsThreshold = newPermitsThreshold;
        }

        public Integer getInitialNrOfPermits() {
            return this.initialNrOfPermits;
        }

        public void setInitialNrOfPermits(Integer initialNrOfPermits) {
            this.initialNrOfPermits = initialNrOfPermits;
        }

        public Integer getNrOfNewPermits() {
            if (this.nrOfNewPermits == null || this.nrOfNewPermits <= 0) {
                return getInitialNrOfPermits() - getNewPermitsThreshold();
            }
            return this.nrOfNewPermits;
        }

        public void setNrOfNewPermits(Integer nrOfNewPermits) {
            this.nrOfNewPermits = nrOfNewPermits;
        }

        public Integer getNewPermitsThreshold() {
            if (this.newPermitsThreshold == null || this.newPermitsThreshold <= 0) {
                return this.initialNrOfPermits / 2;
            }
            return this.newPermitsThreshold;
        }

        public void setNewPermitsThreshold(Integer newPermitsThreshold) {
            this.newPermitsThreshold = newPermitsThreshold;
        }
    }

    public static class HeartbeatConfiguration {

        private static final long DEFAULT_INTERVAL = 10_000;
        private static final long DEFAULT_TIMEOUT = 7_500;

        /**
         * Enables heartbeat messages between a client and Axon Server. When enabled, the connection will be abandoned
         * if a heartbeat message response <b>is not</b> returned in a timely manner. Defaults to {@code true}.
         */
        private boolean enabled = true;

        /**
         * Interval between consecutive heartbeat message sent in milliseconds. Defaults to {@code 10_000}
         * milliseconds.
         */
        private long interval = DEFAULT_INTERVAL;

        /**
         * The time window within which a response is expected in milliseconds. The connection times out if no response
         * is returned within this window. Defaults to {@code 7_500} milliseconds.
         */
        private long timeout = DEFAULT_TIMEOUT;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getInterval() {
            return interval;
        }

        public void setInterval(long interval) {
            this.interval = interval;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
    }

    public static class EventProcessorConfiguration {

        /**
         * The configuration of each of the processors. The key is the name of the processor, the value represents the
         * settings to use for the processor with that name.
         */
        private final Map<String, ProcessorSettings> processors = new HashMap<>();

        /**
         * Returns the settings for each of the configured processors, by name.
         *
         * @return the settings for each of the configured processors, by name.
         */
        public Map<String, ProcessorSettings> getProcessors() {
            return processors;
        }

        public static class ProcessorSettings {

            /**
             * Configures the desired load balancing strategy for this event processor.
             * <p>
             * The load balancing strategy tells Axon Server how to share the event handling load among all available
             * application instances running this event processor, by moving segments from one instance to another. Note
             * that load balancing is <b>only</b> supported for
             * {@link org.axonframework.eventhandling.StreamingEventProcessor StreamingEventProcessors}, as only
             * {@code StreamingEventProcessors} are capable of splitting the event handling load in segments.
             * <p>
             * As the strategies names may change per Axon Server version it is recommended to check the documentation
             * for the possible strategies.
             * <p>
             * Defaults to {@code "disabled"}.
             */
            private String loadBalancingStrategy = "disabled";

            /**
             * A {@code boolean} dictating whether the configured
             * {@link #getLoadBalancingStrategy() load balancing strategy} is set to be automatically triggered through
             * Axon Server.
             * <p>
             * Note that this is an Axon Server Enterprise feature only! Defaults to {@code false}.
             */
            private boolean automaticBalancing = false;

            /**
             * Returns the load balancing strategy for this event processor. Defaults to {@code "disabled"}.
             *
             * @return The load balancing strategy for this event processor.
             */
            public String getLoadBalancingStrategy() {
                return loadBalancingStrategy;
            }

            /**
             * Sets the load balancing strategy for this event processor.
             *
             * @param loadBalancingStrategy The load balancing strategy for this event processor.
             */
            public void setLoadBalancingStrategy(String loadBalancingStrategy) {
                this.loadBalancingStrategy = loadBalancingStrategy;
            }

            /**
             * Returns whether automatic load balancing is configured, yes or no.
             *
             * @return Whether automatic load balancing is configured, yes or no.
             */
            public boolean shouldAutomaticallyBalance() {
                return automaticBalancing;
            }

            /**
             * Sets the automatic load balancing strategy to the given {@code automaticBalancing}.
             *
             * @param automaticBalancing The {@code boolean} to set as to whether automatic load balancing is enabled or
             *                           disabled.
             */
            public void setAutomaticBalancing(boolean automaticBalancing) {
                this.automaticBalancing = automaticBalancing;
            }
        }
    }

    public static class EventStoreConfiguration {

        /**
         * Whether (automatic) configuration of the AxonServer Event Store is enabled. When {@code false}, the event
         * store will not be implicitly be configured. Defaults to {@code true}.
         * <p>
         * Note that this setting will only affect automatic configuration by Application Containers (such as Spring).
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @SuppressWarnings("unused")
    public static class Builder {

        private final AxonServerConfiguration instance;

        public Builder() {
            instance = new AxonServerConfiguration();
            instance.initialNrOfPermits = 1000;
            instance.nrOfNewPermits = 500;
            instance.newPermitsThreshold = 500;
        }

        public Builder ssl(String certFile) {
            instance.certFile = certFile;
            instance.sslEnabled = true;
            return this;
        }

        public Builder token(String token) {
            instance.token = token;
            return this;
        }

        public Builder context(String context) {
            instance.context = context;
            return this;
        }

        public Builder forceReadFromLeader(boolean forceReadFromLeader) {
            instance.forceReadFromLeader = forceReadFromLeader;
            return this;
        }

        public Builder flowControl(int initialNrOfPermits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.initialNrOfPermits = initialNrOfPermits;
            instance.nrOfNewPermits = nrOfNewPermits;
            instance.newPermitsThreshold = newPermitsThreshold;
            return this;
        }

        public Builder commandFlowControl(int initialNrOfPermits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.setCommandFlowControl(new FlowControlConfiguration(initialNrOfPermits,
                                                                        nrOfNewPermits,
                                                                        newPermitsThreshold));
            return this;
        }

        public Builder queryFlowControl(int initialNrOfPermits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.setQueryFlowControl(new FlowControlConfiguration(initialNrOfPermits,
                                                                      nrOfNewPermits,
                                                                      newPermitsThreshold));
            return this;
        }

        public Builder eventFlowControl(int initialNrOfPermits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.setEventFlowControl(new FlowControlConfiguration(initialNrOfPermits,
                                                                      nrOfNewPermits,
                                                                      newPermitsThreshold));
            return this;
        }

        public Builder setEventSecretKey(String key) {
            instance.setEventSecretKey(key);
            return this;
        }

        public Builder eventCipher(EventCipher eventCipher) {
            instance.eventCipher = eventCipher;
            return this;
        }

        public Builder maxMessageSize(int maxMessageSize) {
            instance.maxMessageSize = maxMessageSize;
            return this;
        }

        public Builder snapshotPrefetch(int snapshotPrefetch) {
            instance.snapshotPrefetch = snapshotPrefetch;
            return this;
        }

        public Builder commandLoadFactor(int commandLoadFactor) {
            instance.commandLoadFactor = commandLoadFactor;
            return this;
        }

        /**
         * Initializes a {@link AxonServerConfiguration} as specified through this Builder.
         *
         * @return a {@link AxonServerConfiguration} as specified through this Builder
         */
        public AxonServerConfiguration build() {
            return instance;
        }

        public Builder servers(String servers) {
            instance.setServers(servers);
            return this;
        }

        public Builder suppressDownloadMessage() {
            instance.setSuppressDownloadMessage(true);
            return this;
        }

        public Builder componentName(String componentName) {
            instance.setComponentName(componentName);
            return this;
        }

        public Builder clientId(String clientId) {
            instance.setClientId(clientId);
            return this;
        }

        public Builder connectTimeout(long timeout) {
            instance.setConnectTimeout(timeout);
            return this;
        }
    }
}
