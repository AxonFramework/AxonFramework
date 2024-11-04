/*
 * Copyright (c) 2010-2024. Axon Framework
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

import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.axoniq.axonserver.grpc.control.NodeInfo;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamSequencingPolicyProvider;
import org.axonframework.axonserver.connector.event.util.EventCipher;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Configuration class provided configurable fields and defaults for anything Axon Server related.
 *
 * @author Marc Gathier
 * @author Steven van Beelen
 * @since 4.0
 */
@ConfigurationProperties(prefix = "axon.axonserver")
public class AxonServerConfiguration {

    private static final int DEFAULT_GRPC_PORT = 8124;
    private static final String DEFAULT_SERVERS = "localhost";
    private static final String DEFAULT_CONTEXT = "default";

    /**
     * Whether (automatic) configuration of the Axon Server Connector is enabled. When {@code false}, the connector will
     * not be implicitly be configured. Defaults to {@code true}.
     * <p>
     * Note that this setting will only affect automatic configuration by Application Containers (such as Spring).
     */
    private boolean enabled = true;

    /**
     * Comma separated list of Axon Server servers. Each element is hostname or hostname:grpcPort. When no grpcPort is
     * specified, default port 8124 is used.
     */
    private String servers = DEFAULT_SERVERS;

    /**
     * Client identifier as it registers itself to Axon Server, must be unique.
     */
    private String clientId = ManagementFactory.getRuntimeMXBean().getName();

    /**
     * The name of this application. Multiple instances of the same application share the same application name, but
     * each must have a different {@link #getClientId() client identifier}. Defaults to {@code spring.application.name}
     * when not defined, and to {@code Unnamed + client identifier} when {@code spring.application.name} isn't set.
     */
    private String componentName;

    /**
     * The token providing access control with Axon Server.
     */
    private String token;

    /**
     * The bounded {@code context} that this application operates in. Defaults to {@code "default"}.
     */
    private String context = DEFAULT_CONTEXT;

    /**
     * The path to the certificate file used for SSL. Note the path is only used when
     * {@link #isSslEnabled() SSL is enabled.}
     */
    private String certFile;

    /**
     * A toggle dictating whether to use TLS for the connection to Axon Server.
     */
    private boolean sslEnabled;

    /**
     * Initial number of permits send for message streams (events, commands, queries).
     *
     * @deprecated In favor of {@code permits}.
     */
    @Deprecated
    private Integer initialNrOfPermits;

    /**
     * The initial number of permits send for message streams (events, commands, queries). Defaults to {@code 5000}
     * permits.
     */
    private Integer permits = 5000;

    /**
     * Additional number of permits send for message streams (events, commands, queries) when application is ready for
     * more messages.
     * <p>
     * A value of {@code null}, 0, and negative values will have the client request the number of permits required to
     * get from the "new-permits-threshold" to "initial-nr-of-permits".
     */
    private Integer nrOfNewPermits = null;

    /**
     * Threshold at which the application sends new permits to server.
     * <p>
     * A value of {@code null}, 0, and negative values will have the threshold set to 50% of
     * {@link #getPermits() the initial number of permits}.
     */
    private Integer newPermitsThreshold = null;

    /**
     * Specific {@link FlowControlConfiguration flow control settings} for the event message stream.
     * <p>
     * When not specified (null) the top-level flow control properties {@code permits}, {@code nrOfNewPermits} and
     * {@code newPermitsThreshold} are used.
     */
    private FlowControlConfiguration eventFlowControl;

    /**
     * Specific {@link FlowControlConfiguration flow control settings} for the query message stream.
     * <p>
     * When not specified (null) the top-level flow control properties {@code permits}, {@code nrOfNewPermits} and
     * {@code newPermitsThreshold} are used.
     */
    private FlowControlConfiguration queryFlowControl;

    /**
     * Specific {@link FlowControlConfiguration flow control settings} for the command message stream.
     * <p>
     * When not specified (null) the top-level flow control properties {@code permits}, {@code nrOfNewPermits} and
     * {@code newPermitsThreshold} are used.
     */
    private FlowControlConfiguration commandFlowControl;

    /**
     * A toggle dictating whether to invoke query handlers directly if they are registered in the local environment.
     * Defaults to {@code false}.
     */
    private boolean shortcutQueriesToLocalHandlers = false;

    /**
     * The number of threads executing commands. Defaults to {@code 10} threads.
     */
    private int commandThreads = 10;

    /**
     * The number of threads executing queries. Defaults to {@code 10} threads.
     */
    private int queryThreads = 10;

    /**
     * The interval (in ms.) application sends status updates on event processors to Axon Server. Defaults to
     * {@code 500} milliseconds.
     */
    private int processorsNotificationRate = 500;

    /**
     * The initial delay (in ms.) before application sends first status update on event processors to Axon Server.
     * Defaults to {@code 5000} milliseconds.
     */
    private int processorsNotificationInitialDelay = 5000;

    /**
     * An {@link EventCipher} which is used to encrypt and decrypt events and snapshots. Defaults to
     * {@link EventCipher#EventCipher()}.
     *
     * @deprecated Through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java
     * Connector</a> project.
     */
    @Deprecated
    private EventCipher eventCipher = new EventCipher();

    /**
     * The timeout (in ms) for keep alive requests. Defaults to {@code 5000} milliseconds.
     */
    private long keepAliveTimeout = 5000;

    /**
     * The interval (in ms) for keep alive requests, 0 is keep-alive disabled. Defaults to {@code 1000} milliseconds.
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
     *
     * @deprecated Through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java
     * Connector</a> project, which enables the download message in absence of configured servers.
     */
    @Deprecated
    private boolean suppressDownloadMessage = false;

    /**
     * The gRPC max inbound message size. Defaults to {@code 0}, keeping the default value from the connector.
     */
    private int maxMessageSize = 0;

    /**
     * The timeout (in milliseconds) to wait for response on commit. Defaults to {@code 10_000} milliseconds.
     */
    private int commitTimeout = 10_000;

    /**
     * Flag that allows blacklisting of Event types to be disabled. Disabling this may have serious performance impact,
     * as it requires all messages from Axon Server to be sent to clients, even if a Client is unable to process the
     * message.
     * <p>
     * Default is to have blacklisting enabled.
     *
     * @deprecated In favor of the {@code eventBlockListingEnabled} property.
     */
    @Deprecated
    private boolean disableEventBlacklisting = false;

    /**
     * Flag that allows block-listing of event types to be enabled.
     * <p>
     * Disabling this may have serious performance impact, as it requires all {@link EventMessage events} from Axon
     * Server to be sent to clients, even if a client is unable to process the event. Default is to have block-listing
     * enabled.
     */
    private boolean eventBlockListingEnabled = true;

    /**
     * The number of messages that may be in-transit on the network/grpc level when streaming data from the server.
     * <p>
     * Setting this to 0 (or a negative value) will disable buffering, and requires each message sent by the server to
     * be acknowledged before the next message may be sent. Defaults to 500.
     *
     * @deprecated This property is no longer used through adjustments in the <a
     * href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java Connector</a> project.
     */
    @Deprecated
    private int maxGrpcBufferedMessages = 500;

    /**
     * An {@code int} representing the fixed value of load factor sent to Axon Server for any command's subscription if
     * no specific implementation of CommandLoadFactorProvider is configured. The default value is {@code 100}.
     */
    private int commandLoadFactor = 100;

    /**
     * A value representing the maximum time in milliseconds a request for the initial Axon Server connection may last.
     * Defaults to 5000 (5 seconds).
     */
    private long connectTimeout = 5000;

    /**
     * Sets the amount of time in milliseconds to wait in between attempts to connect to Axon Server. A single attempt
     * involves connecting to each of the configured {@link #getServers() servers}.
     * <p>
     * Defaults to 2000 (2 seconds).
     */
    private long reconnectInterval = 2000;

    /**
     * Indicates whether it is OK to query events from the local Axon Server node - the node the client is currently
     * connected to. This means that the client will probably get stale events since all events my not be replicated to
     * this node yet. Can be used when the criteria for eventual consistency is less strict. It will spread the load for
     * querying events - not all requests will go to the leader of the cluster anymore.
     * <p>
     * If Axon Server SE is used, this property has no effect.
     */
    private boolean forceReadFromLeader = false;

    /**
     * Indicates whether the {@link AxonServerConnectionManager} should always reconnect through the
     * {@link #getServers() servers} or try to reconnect with the server it just lost the connection with.
     * <p>
     * When {@code true} (default), the  {@code AxonServerConnectionManager} will contact the servers for a new
     * destination each time a connection is dropped. When {@code false}, the connector will first attempt to
     * re-establish a connection to the node it was previously connected to. When that fails, only then will it contact
     * the servers.
     * <p>
     * Default to {@code true}, forcing the failed connection to be abandoned and a new one to be requested via the
     * routing servers.
     */
    private boolean forceReconnectThroughServers = true;

    /**
     * Defines the number of threads that should be used for connection management activities by the
     * {@link AxonServerConnectionFactory} used by the {@link AxonServerConnectionManager}.
     * <p>
     * This includes activities related to connecting to Axon Server, setting up instruction streams, sending and
     * validating heartbeats, etc.
     * <p>
     * Defaults to a pool size of {@code 2} threads.
     */
    private int connectionManagementThreadPoolSize = 2;

    /**
     * Configuration specifics on sending heartbeat messages to ensure a fully operational end-to-end connection with
     * Axon Server.
     */
    private HeartbeatConfiguration heartbeat = new HeartbeatConfiguration();

    /**
     * Properties describing the settings for {@link EventProcessor EventProcessors}.
     */
    private Eventhandling eventhandling = new Eventhandling();

    /**
     * Properties describing the settings for the {@link AxonServerEventStore EventStore}.
     */
    private EventStoreConfiguration eventStoreConfiguration = new EventStoreConfiguration();

    /**
     * The configuration of each of the persistent streams. The key is the identifier of the message source, the value
     * represents the settings to use for the related persistent stream.
     */
    private final Map<String, PersistentStreamSettings> persistentStreams = new HashMap<>();

    /**
     * Instantiate a {@link Builder} to create an {@link AxonServerConfiguration}.
     *
     * @return a {@link Builder} to be able to create an {@link AxonServerConfiguration}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a default {@link AxonServerConfiguration}.
     */
    public AxonServerConfiguration() {
    }

    /**
     * Whether (automatic) configuration of the Axon Server Connector is enabled. When {@code false}, the connector will
     * not be implicitly be configured. Defaults to {@code true}.
     * <p>
     * Note that this setting will only affect automatic configuration by Application Containers (such as Spring).
     *
     * @return Whether (automatic) configuration of the Axon Server Connector is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isShortcutQueriesToLocalHandlers() {
        return shortcutQueriesToLocalHandlers;
    }

    public void setShortcutQueriesToLocalHandlers(boolean shortcutQueriesToLocalHandlers) {
        this.shortcutQueriesToLocalHandlers = shortcutQueriesToLocalHandlers;
    }

    /**
     * Set whether (automatic) configuration of the Axon Server Connector is enabled. When {@code false}, the connector
     * will not be implicitly be configured. Defaults to {@code true}.
     * <p>
     * Note that this setting will only affect automatic configuration by Application Containers (such as Spring).
     *
     * @param enabled Whether (automatic) configuration of the Axon Server Connector is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Comma separated list of Axon Server servers. Each element is hostname or hostname:grpcPort. When no grpcPort is
     * specified, default port 8124 is used.
     *
     * @return Comma separated list of Axon Server servers.
     */
    public String getServers() {
        return servers;
    }

    /**
     * A list of {@link NodeInfo} instances based on the comma separated list of {@link #getServers()}.
     *
     * @return A list of {@link NodeInfo} instances based on the comma separated list of {@link #getServers()}.
     * @deprecated In favor of the {@link AxonServerConnectionManager} itself being in charge of parsing the
     * {@link #getServers()} list into a {@link NodeInfo} collection.
     */
    @Deprecated
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

    /**
     * Set the comma separated list of Axon Server servers. Each element is hostname or hostname:grpcPort. When no
     * grpcPort is specified, default port 8124 is used.
     *
     * @param routingServers The comma separated list of Axon Server servers to connect with.
     */
    public void setServers(String routingServers) {
        this.servers = routingServers;
    }

    /**
     * The client identifier as it registers itself to Axon Server, must be unique.
     *
     * @return The client identifier as it registers itself to Axon Server, must be unique.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Sets the client identifier as it registers itself to Axon Server, must be unique.
     *
     * @param clientId The client identifier as it registers itself to Axon Server, must be unique.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * The name of this application. Multiple instances of the same application share the same application name, but
     * each must have a different {@link #getClientId() client identifier}. Defaults to {@code spring.application.name}
     * when not defined, and to {@code Unnamed + client identifier} when {@code spring.application.name} isn't set.
     *
     * @return The name of this application.
     */
    public String getComponentName() {
        return componentName == null
                ? System.getProperty("axon.application.name", "Unnamed-" + clientId)
                : componentName;
    }

    /**
     * Sets the name of this application. Multiple instances of the same application share the same application name,
     * but each must have a different {@link #getClientId() client identifier}. Defaults to
     * {@code spring.application.name} when not defined, and to {@code Unnamed + client identifier} when
     * {@code spring.application.name} isn't set.
     *
     * @param componentName The name of this application.
     */
    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    /**
     * The token providing access control with Axon Server.\
     *
     * @return The token providing access control with Axon Server.
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets the token providing access control with Axon Server.
     *
     * @param token The token providing access control with Axon Server.
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * The bounded {@code context} that this application operates in. Defaults to {@code "default"}.
     *
     * @return The bounded {@code context} that this application operates in.
     */
    public String getContext() {
        return context;
    }

    /**
     * Sets the bounded {@code context} that this application operates in. Defaults to {@code "default"}.
     *
     * @param context The bounded {@code context} that this application operates in.
     */
    public void setContext(String context) {
        this.context = context;
    }

    /**
     * The path to the certificate file used for SSL. Note the path is only used when
     * {@link #isSslEnabled() SSL is enabled.}
     *
     * @return The path to the certificate file used for SSL.
     */
    public String getCertFile() {
        return certFile;
    }

    /**
     * Sets the path to the certificate file used for SSL. Note the path is only used when
     * {@link #isSslEnabled() SSL is enabled.}
     *
     * @param certFile The path to the certificate file used for SSL.
     */
    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    /**
     * A toggle dictating whether to use TLS for the connection to Axon Server.\
     *
     * @return A toggle dictating whether to use TLS for the connection to Axon Server.
     */
    public boolean isSslEnabled() {
        return sslEnabled;
    }

    /**
     * Defines whether to use TLS for the connection to Axon Server.
     *
     * @param sslEnabled The toggle dictating whether to use TLS for the connection to Axon Server.
     */
    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    /**
     * @deprecated In favor of {@link #getPermits()}
     */
    @Deprecated
    public Integer getInitialNrOfPermits() {
        return initialNrOfPermits;
    }

    /**
     * @deprecated In favor of {@link #setPermits(Integer)}
     */
    @Deprecated
    public void setInitialNrOfPermits(Integer initialNrOfPermits) {
        this.permits = initialNrOfPermits;
    }

    /**
     * The initial number of permits send for message streams (events, commands, queries). Defaults to {@code 5000}
     * permits.
     *
     * @return The initial number of permits send for message streams (events, commands, queries).
     */
    public Integer getPermits() {
        return permits;
    }

    /**
     * Sets the initial number of permits send for message streams (events, commands, queries). Defaults to {@code 5000}
     * permits.
     *
     * @param permits The initial number of permits send for message streams (events, commands, queries).
     */
    public void setPermits(Integer permits) {
        this.permits = permits;
    }

    /**
     * Additional number of permits send for message streams (events, commands, queries) when application is ready for
     * more messages.
     * <p>
     * A value of {@code null}, 0, and negative values will have the client request the number of permits required to
     * get from the "new-permits-threshold" to "initial-nr-of-permits".
     *
     * @return The additional number of permits send for message streams (events, commands, queries) when application is
     * ready for more messages.
     */
    public Integer getNrOfNewPermits() {
        if (nrOfNewPermits == null || nrOfNewPermits <= 0) {
            return getPermits() - getNewPermitsThreshold();
        }
        return nrOfNewPermits;
    }

    /**
     * Sets the additional number of permits send for message streams (events, commands, queries) when application is
     * ready for more messages.
     * <p>
     * A value of {@code null}, 0, and negative values will have the client request the number of permits required to
     * get from the "new-permits-threshold" to "initial-nr-of-permits".
     *
     * @param nrOfNewPermits The additional number of permits send for message streams (events, commands, queries) when
     *                       application is ready for more messages.
     */
    public void setNrOfNewPermits(Integer nrOfNewPermits) {
        this.nrOfNewPermits = nrOfNewPermits;
    }

    /**
     * The threshold at which the application sends new permits to server.
     * <p>
     * A value of {@code null}, 0, and negative values will have the threshold set to 50% of
     * {@link #getPermits() the initial number of permits}.
     *
     * @return The threshold at which the application sends new permits to server.
     */
    public Integer getNewPermitsThreshold() {
        if (newPermitsThreshold == null || newPermitsThreshold <= 0) {
            return getPermits() / 2;
        }
        return newPermitsThreshold;
    }

    /**
     * Sets the threshold at which the application sends new permits to server.
     * <p>
     * A value of {@code null}, 0, and negative values will have the threshold set to 50% of
     * {@link #getPermits() the initial number of permits}.
     *
     * @param newPermitsThreshold The threshold at which the application sends new permits to server.
     */
    public void setNewPermitsThreshold(Integer newPermitsThreshold) {
        this.newPermitsThreshold = newPermitsThreshold;
    }

    /**
     * @deprecated Through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java
     * Connector</a> project.
     */
    @Deprecated
    public EventCipher getEventCipher() {
        return eventCipher;
    }

    /**
     * @deprecated Through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java
     * Connector</a> project.
     */
    @Deprecated
    private void setEventSecretKey(String key) {
        if (key != null && !key.isEmpty()) {
            eventCipher = new EventCipher(key.getBytes(StandardCharsets.US_ASCII));
        }
    }

    /**
     * Specific flow control settings for the event message stream.
     * <p>
     * When not specified (null) the top-level flow control properties {@code permits}, {@code nrOfNewPermits} and
     * {@code newPermitsThreshold} are used.
     *
     * @return Specific flow control settings for the event message stream.
     */
    public FlowControlConfiguration getEventFlowControl() {
        if (eventFlowControl == null) {
            return new FlowControlConfiguration(getPermits(), getNrOfNewPermits(), getNewPermitsThreshold());
        }
        return eventFlowControl;
    }

    /**
     * Sets specific {@link FlowControlConfiguration flow control settings} for the event message stream.
     * <p>
     * When not specified (null) the top-level flow control properties {@code permits}, {@code nrOfNewPermits} and
     * {@code newPermitsThreshold} are used.
     *
     * @param eventFlowControl Specific {@link FlowControlConfiguration flow control settings} for the event message
     *                         stream.
     */
    public void setEventFlowControl(FlowControlConfiguration eventFlowControl) {
        this.eventFlowControl = eventFlowControl;
    }

    /**
     * Specific {@link FlowControlConfiguration flow control settings} for the query message stream.
     * <p>
     * When not specified (null) the top-level flow control properties {@code permits}, {@code nrOfNewPermits} and
     * {@code newPermitsThreshold} are used.
     *
     * @return Specific {@link FlowControlConfiguration flow control settings} for the query message stream.
     */
    public FlowControlConfiguration getQueryFlowControl() {
        if (queryFlowControl == null) {
            return new FlowControlConfiguration(getPermits(), getNrOfNewPermits(), getNewPermitsThreshold());
        }
        return queryFlowControl;
    }

    /**
     * Sets specific {@link FlowControlConfiguration flow control settings} for the query message stream.
     * <p>
     * When not specified (null) the top-level flow control properties {@code permits}, {@code nrOfNewPermits} and
     * {@code newPermitsThreshold} are used.
     *
     * @param queryFlowControl Specific {@link FlowControlConfiguration flow control settings} for the query message
     *                         stream.
     */
    public void setQueryFlowControl(FlowControlConfiguration queryFlowControl) {
        this.queryFlowControl = queryFlowControl;
    }

    /**
     * Specific {@link FlowControlConfiguration flow control settings} for the command message stream.
     * <p>
     * When not specified (null) the top-level flow control properties {@code permits}, {@code nrOfNewPermits} and
     * {@code newPermitsThreshold} are used.
     *
     * @return Specific {@link FlowControlConfiguration flow control settings} for the command message stream.
     */
    public FlowControlConfiguration getCommandFlowControl() {
        if (commandFlowControl == null) {
            return new FlowControlConfiguration(getPermits(), getNrOfNewPermits(), getNewPermitsThreshold());
        }
        return commandFlowControl;
    }

    /**
     * Sets specific {@link FlowControlConfiguration flow control settings} for the command message stream.
     * <p>
     * When not specified (null) the top-level flow control properties {@code permits}, {@code nrOfNewPermits} and
     * {@code newPermitsThreshold} are used.
     *
     * @param commandFlowControl Specific {@link FlowControlConfiguration flow control settings} for the command message
     *                           stream.
     */
    public void setCommandFlowControl(FlowControlConfiguration commandFlowControl) {
        this.commandFlowControl = commandFlowControl;
    }

    /**
     * The default {@link FlowControlConfiguration flow control settings} used when no specific
     * {@link #getCommandFlowControl() command}, {@link #getEventFlowControl() event}, or
     * {@link #getQueryFlowControl() query} flow control settings are provided.
     *
     * @return The default {@link FlowControlConfiguration flow control settings} used when no specific
     * {@link #getCommandFlowControl() command}, {@link #getEventFlowControl() event}, or
     * {@link #getQueryFlowControl() query} flow control settings are provided.
     */
    public FlowControlConfiguration getDefaultFlowControlConfiguration() {
        return new FlowControlConfiguration(permits, nrOfNewPermits, newPermitsThreshold);
    }

    /**
     * The number of threads executing commands. Defaults to {@code 10} threads.
     *
     * @return The number of threads executing commands.
     */
    public int getCommandThreads() {
        return commandThreads;
    }

    /**
     * Sets the number of threads executing commands. Defaults to {@code 10} threads.
     *
     * @param commandThreads The number of threads executing commands.
     */
    public void setCommandThreads(int commandThreads) {
        this.commandThreads = commandThreads;
    }

    /**
     * The number of threads executing queries. Defaults to {@code 10} threads.
     *
     * @return The number of threads executing queries.
     */
    public int getQueryThreads() {
        return queryThreads;
    }

    /**
     * Sets the number of threads executing queries. Defaults to {@code 10} threads.
     *
     * @param queryThreads The number of threads executing queries.
     */
    public void setQueryThreads(int queryThreads) {
        this.queryThreads = queryThreads;
    }

    /**
     * The interval (in ms.) application sends status updates on event processors to Axon Server. Defaults to
     * {@code 500} milliseconds.
     *
     * @return The interval (in ms.) application sends status updates on event processors to Axon Server.
     */
    public int getProcessorsNotificationRate() {
        return processorsNotificationRate;
    }

    /**
     * Sets the interval (in ms.) application sends status updates on event processors to Axon Server. Defaults to
     * {@code 500} milliseconds.
     *
     * @param processorsNotificationRate The interval (in ms.) application sends status updates on event processors to
     *                                   Axon Server.
     */
    public void setProcessorsNotificationRate(int processorsNotificationRate) {
        this.processorsNotificationRate = processorsNotificationRate;
    }

    /**
     * The initial delay (in ms.) before application sends first status update on event processors to Axon Server.
     * Defaults to {@code 5000} milliseconds.
     *
     * @return The initial delay (in ms.) before application sends first status update on event processors to Axon
     * Server.
     */
    public int getProcessorsNotificationInitialDelay() {
        return processorsNotificationInitialDelay;
    }

    /**
     * Sets the initial delay (in ms.) before application sends first status update on event processors to Axon Server.
     * Defaults to {@code 5000} milliseconds.
     *
     * @param processorsNotificationInitialDelay The initial delay (in ms.) before application sends first status update
     *                                           on event processors to Axon Server.
     */
    public void setProcessorsNotificationInitialDelay(int processorsNotificationInitialDelay) {
        this.processorsNotificationInitialDelay = processorsNotificationInitialDelay;
    }

    /**
     * The timeout (in ms) for keep alive requests. Defaults to {@code 5000} milliseconds.
     *
     * @return The timeout (in ms) for keep alive requests.
     */
    public long getKeepAliveTimeout() {
        return this.keepAliveTimeout;
    }

    /**
     * Sets the timeout (in ms) for keep alive requests. Defaults to {@code 5000} milliseconds.
     *
     * @param keepAliveTimeout The timeout (in ms) for keep alive requests.
     */
    public void setKeepAliveTimeout(long keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    /**
     * The interval (in ms) for keep alive requests, 0 is keep-alive disabled. Defaults to {@code 1000} milliseconds.
     *
     * @return The interval (in ms) for keep alive requests, 0 is keep-alive disabled.
     */
    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Sets the interval (in ms) for keep alive requests, 0 is keep-alive disabled. Defaults to {@code 1000}
     * milliseconds.
     *
     * @param keepAliveTime The interval (in ms) for keep alive requests, 0 is keep-alive disabled.
     */
    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    /**
     * An {@code int} indicating the maximum number of Aggregate snapshots which will be retrieved. Defaults to
     * {@code 1}.
     *
     * @return An {@code int} indicating the maximum number of Aggregate snapshots which will be retrieved.
     */
    public int getSnapshotPrefetch() {
        return snapshotPrefetch;
    }

    /**
     * Sets the maximum number of Aggregate snapshots which will be retrieved. Defaults to {@code 1}.
     *
     * @param snapshotPrefetch The maximum number of Aggregate snapshots which will be retrieved.
     */
    public void setSnapshotPrefetch(int snapshotPrefetch) {
        this.snapshotPrefetch = snapshotPrefetch;
    }

    /**
     * @deprecated Through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java
     * Connector</a> project, which enables the download message in absence of configured servers.
     */
    @Deprecated
    public boolean getSuppressDownloadMessage() {
        return suppressDownloadMessage;
    }

    /**
     * @deprecated Through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java
     * Connector</a> project, which enables the download message in absence of configured servers.
     */
    @Deprecated
    public void setSuppressDownloadMessage(boolean suppressDownloadMessage) {
        this.suppressDownloadMessage = suppressDownloadMessage;
    }

    /**
     * The gRPC max inbound message size. Defaults to {@code 0}, keeping the default value from the connector.
     *
     * @return The gRPC max inbound message size.
     */
    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Sets the gRPC max inbound message size. Defaults to {@code 0}, keeping the default value from the connector.
     *
     * @param maxMessageSize The gRPC max inbound message size.
     */
    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    /**
     * The timeout (in milliseconds) to wait for response on commit. Defaults to {@code 10_000} milliseconds.
     *
     * @return The timeout (in milliseconds) to wait for response on commit.
     */
    public int getCommitTimeout() {
        return commitTimeout;
    }

    /**
     * Sets the timeout (in milliseconds) to wait for response on commit. Defaults to {@code 10_000} milliseconds.
     *
     * @param commitTimeout The timeout (in milliseconds) to wait for response on commit.
     */
    public void setCommitTimeout(int commitTimeout) {
        this.commitTimeout = commitTimeout;
    }

    /**
     * @deprecated In favor of {@link #setEventBlockListingEnabled(boolean)}.
     */
    @Deprecated
    public boolean isDisableEventBlacklisting() {
        return disableEventBlacklisting;
    }

    /**
     * @deprecated In favor of {@link #setEventBlockListingEnabled(boolean)}.
     */
    @Deprecated
    public void setDisableEventBlacklisting(boolean disableEventBlacklisting) {
        this.eventBlockListingEnabled = !disableEventBlacklisting;
    }

    /**
     * Flag that allows block-listing of event types to be enabled.
     * <p>
     * Disabling this may have serious performance impact, as it requires all {@link EventMessage events} from Axon
     * Server to be sent to clients, even if a client is unable to process the event. Default is to have block-listing
     * enabled.
     *
     * @return Flag that allows block-listing of event types to be enabled.
     */
    public boolean isEventBlockListingEnabled() {
        return eventBlockListingEnabled;
    }

    /**
     * Sets flag that allows block-listing of event types to be enabled.
     * <p>
     * Disabling this may have serious performance impact, as it requires all {@link EventMessage events} from Axon
     * Server to be sent to clients, even if a client is unable to process the event. Default is to have block-listing
     * enabled.
     *
     * @param eventBlockListingEnabled Flag that allows block-listing of event types to be enabled.
     */
    public void setEventBlockListingEnabled(boolean eventBlockListingEnabled) {
        this.eventBlockListingEnabled = eventBlockListingEnabled;
    }

    /**
     * @deprecated This property is no longer used through adjustments in the <a
     * href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java Connector</a> project.
     */
    @Deprecated
    public int getMaxGrpcBufferedMessages() {
        return maxGrpcBufferedMessages;
    }

    /**
     * @deprecated This property is no longer used through adjustments in the <a
     * href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java Connector</a> project.
     */
    @Deprecated
    public void setMaxGrpcBufferedMessages(int maxGrpcBufferedMessages) {
        this.maxGrpcBufferedMessages = maxGrpcBufferedMessages;
    }

    /**
     * An {@code int} representing the fixed value of load factor sent to Axon Server for any command's subscription if
     * no specific implementation of CommandLoadFactorProvider is configured. The default value is {@code 100}.
     *
     * @return An {@code int} representing the fixed value of load factor sent to Axon Server for any command's
     * subscription if no specific implementation of CommandLoadFactorProvider is configured.
     */
    public int getCommandLoadFactor() {
        return commandLoadFactor;
    }

    /**
     * Sets an {@code int} representing the fixed value of load factor sent to Axon Server for any command's
     * subscription if no specific implementation of CommandLoadFactorProvider is configured. The default value is
     * {@code 100}.
     *
     * @param commandLoadFactor An {@code int} representing the fixed value of load factor sent to Axon Server for any
     *                          command's subscription if no specific implementation of CommandLoadFactorProvider is
     *                          configured.
     */
    public void setCommandLoadFactor(int commandLoadFactor) {
        this.commandLoadFactor = commandLoadFactor;
    }

    /**
     * A value representing the maximum time in milliseconds a request for the initial Axon Server connection may last.
     * Defaults to 5000 (5 seconds).
     *
     * @return A value representing the maximum time in milliseconds a request for the initial Axon Server connection
     * may last.
     */
    public long getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets the value representing the maximum time in milliseconds a request for the initial Axon Server connection may
     * last. Defaults to 5000 (5 seconds).
     *
     * @param connectTimeout The value representing the maximum time in milliseconds a request for the initial Axon
     *                       Server connection may last.
     */
    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * The amount of time in milliseconds to wait in between attempts to connect to Axon Server. A single attempt
     * involves connecting to each of the configured {@link #getServers() servers}.
     * <p>
     * Defaults to 2000 (2 seconds).
     *
     * @return The amount of time in milliseconds to wait in between attempts to connect to Axon Server.
     */
    public long getReconnectInterval() {
        return reconnectInterval;
    }

    /**
     * Sets the amount of time in milliseconds to wait in between attempts to connect to Axon Server. A single attempt
     * involves connecting to each of the configured {@link #getServers() servers}.
     * <p>
     * Defaults to 2000 (2 seconds).
     *
     * @param reconnectInterval The amount of time in milliseconds to wait in between attempts to connect to Axon
     *                          Server.
     */
    public void setReconnectInterval(long reconnectInterval) {
        this.reconnectInterval = reconnectInterval;
    }

    /**
     * Indicates whether it is OK to query events from the local Axon Server node - the node the client is currently
     * connected to. This means that the client will probably get stale events since all events my not be replicated to
     * this node yet. Can be used when the criteria for eventual consistency is less strict. It will spread the load for
     * querying events - not all requests will go to the leader of the cluster anymore.
     * <p>
     * If Axon Server SE is used, this property has no effect.
     *
     * @return An indication whether it is OK to query events from the local Axon Server node - the node the client is
     * currently connected to.
     */
    public boolean isForceReadFromLeader() {
        return forceReadFromLeader;
    }

    /**
     * Sets the indicator whether it is OK to query events from the local Axon Server node - the node the client is
     * currently connected to. This means that the client will probably get stale events since all events my not be
     * replicated to this node yet. Can be used when the criteria for eventual consistency is less strict. It will
     * spread the load for querying events - not all requests will go to the leader of the cluster anymore.
     * <p>
     * If Axon Server SE is used, this property has no effect.
     *
     * @param forceReadFromLeader The indicator whether it is OK to query events from the local Axon Server node - the
     *                            node the client is currently connected to.
     */
    public void setForceReadFromLeader(boolean forceReadFromLeader) {
        this.forceReadFromLeader = forceReadFromLeader;
    }

    /**
     * Indicates whether the {@link AxonServerConnectionManager} should always reconnect through the
     * {@link #getServers() servers} or try to reconnect with the server it just lost the connection with.
     * <p>
     * When {@code true} (default), the  {@code AxonServerConnectionManager} will contact the servers for a new
     * destination each time a connection is dropped. When {@code false}, the connector will first attempt to
     * re-establish a connection to the node it was previously connected to. When that fails, only then will it contact
     * the servers.
     * <p>
     * Default to {@code true}, forcing the failed connection to be abandoned and a new one to be requested via the
     * routing servers.
     *
     * @return An indication whether the {@link AxonServerConnectionManager} should always reconnect through the
     * {@link #getServers() servers} or try to reconnect with the server it just lost the connection with.
     */
    public boolean isForceReconnectThroughServers() {
        return forceReconnectThroughServers;
    }

    /**
     * Sets the indicator whether the {@link AxonServerConnectionManager} should always reconnect through the
     * {@link #getServers() servers} or try to reconnect with the server it just lost the connection with.
     * <p>
     * When {@code true} (default), the  {@code AxonServerConnectionManager} will contact the servers for a new
     * destination each time a connection is dropped. When {@code false}, the connector will first attempt to
     * re-establish a connection to the node it was previously connected to. When that fails, only then will it contact
     * the servers.
     * <p>
     * Default to {@code true}, forcing the failed connection to be abandoned and a new one to be requested via the
     * routing servers.
     *
     * @param forceReconnectThroughServers The indicator whether the {@link AxonServerConnectionManager} should always
     *                                     reconnect through the {@link #getServers() servers} or try to reconnect with
     *                                     the server it just lost the connection with.
     */
    public void setForceReconnectThroughServers(boolean forceReconnectThroughServers) {
        this.forceReconnectThroughServers = forceReconnectThroughServers;
    }

    /**
     * The number of threads that should be used for connection management activities by the
     * {@link AxonServerConnectionFactory} used by the {@link AxonServerConnectionManager}.
     * <p>
     * This includes activities related to connecting to Axon Server, setting up instruction streams, sending and
     * validating heartbeats, etc.
     * <p>
     * Defaults to a pool size of {@code 2} threads.
     *
     * @return The number of threads that should be used for connection management activities by the
     * {@link AxonServerConnectionFactory} used by the {@link AxonServerConnectionManager}.
     */
    public int getConnectionManagementThreadPoolSize() {
        return connectionManagementThreadPoolSize;
    }

    /**
     * Define the number of threads that should be used for connection management activities by the
     * {@link AxonServerConnectionFactory} used by the {@link AxonServerConnectionManager}.
     * <p>
     * This includes activities related to connecting to Axon Server, setting up instruction streams, sending and
     * validating heartbeats, etc.
     * <p>
     * Defaults to a pool size of {@code 2} threads.
     *
     * @param connectionManagementThreadPoolSize The number of threads that should be used for connection management
     *                                           activities by the {@link AxonServerConnectionFactory} used by the
     *                                           {@link AxonServerConnectionManager}.
     */
    public void setConnectionManagementThreadPoolSize(int connectionManagementThreadPoolSize) {
        this.connectionManagementThreadPoolSize = connectionManagementThreadPoolSize;
    }

    /**
     * The configuration specifics on sending heartbeat messages to ensure a fully operational end-to-end connection
     * with Axon Server.
     *
     * @return The configuration specifics on sending heartbeat messages to ensure a fully operational end-to-end
     * connection with Axon Server.
     */
    public HeartbeatConfiguration getHeartbeat() {
        return heartbeat;
    }

    /**
     * Sets the configuration specifics on sending heartbeat messages to ensure a fully operational end-to-end
     * connection with Axon Server.
     *
     * @param heartbeat The configuration specifics on sending heartbeat messages to ensure a fully operational
     *                  end-to-end connection with Axon Server.
     */
    public void setHeartbeat(HeartbeatConfiguration heartbeat) {
        this.heartbeat = heartbeat;
    }

    /**
     * Return the configured {@link Eventhandling} of this application for Axon Server.
     *
     * @return The configured {@link Eventhandling} of this application for Axon Server.
     */
    public Eventhandling getEventhandling() {
        return eventhandling;
    }

    /**
     * Set the {@link Eventhandling} of this application for Axon Server
     *
     * @param eventhandling The {@link Eventhandling} to set for this application.
     */
    public void setEventhandling(Eventhandling eventhandling) {
        this.eventhandling = eventhandling;
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
     * Returns the settings for each of the configured persistent streams, by name.
     *
     * @return The settings for each of the configured persistent streams, by name.
     */
    public Map<String, PersistentStreamSettings> getPersistentStreams() {
        return persistentStreams;
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
         *
         * @deprecated In favor of {@code permits}.
         */
        @Deprecated
        private Integer initialNrOfPermits;

        /**
         * The initial number of permits send for message streams (events, commands, queries).
         */
        private Integer permits;

        /**
         * Additional number of permits send for message streams (events, commands, queries) when application is ready
         * for more messages.
         * <p>
         * A value of {@code null}, 0, and negative values will have the client request the number of permits required
         * to get from the "new-permits-threshold" to "initial-nr-of-permits".
         */
        private Integer nrOfNewPermits;

        /**
         * Threshold at which application sends new permits to server.
         * <p>
         * A value of {@code null}, 0, and negative values will have the threshold set to 50% of
         * "initial-nr-of-permits".
         */
        private Integer newPermitsThreshold;

        /**
         * Construct a {@link FlowControlConfiguration}.
         *
         * @param permits             Initial nr of new permits.
         * @param nrOfNewPermits      Additional number of permits when application is ready for message.
         * @param newPermitsThreshold Threshold at which application sends new permits to server.
         */
        public FlowControlConfiguration(Integer permits,
                                        Integer nrOfNewPermits,
                                        Integer newPermitsThreshold) {
            this.permits = permits;
            this.nrOfNewPermits = nrOfNewPermits;
            this.newPermitsThreshold = newPermitsThreshold;
        }

        /**
         * @deprecated In favor of {@link #getPermits()}.
         */
        @Deprecated
        public Integer getInitialNrOfPermits() {
            return this.permits;
        }

        /**
         * @deprecated In favor of {@link #setPermits(Integer)}.
         */
        @Deprecated
        public void setInitialNrOfPermits(Integer initialNrOfPermits) {
            this.permits = initialNrOfPermits;
        }

        /**
         * The initial number of permits send for message streams (events, commands, queries). Defaults to {@code 5000}
         * permits.
         *
         * @return The initial number of permits send for message streams (events, commands, queries).
         */
        public Integer getPermits() {
            return permits;
        }

        /**
         * Sets the initial number of permits send for message streams (events, commands, queries). Defaults to
         * {@code 5000} permits.
         *
         * @param permits The initial number of permits send for message streams (events, commands, queries).
         */
        public void setPermits(Integer permits) {
            this.permits = permits;
        }

        /**
         * Additional number of permits send for message streams (events, commands, queries) when application is ready
         * for more messages.
         * <p>
         * A value of {@code null}, 0, and negative values will have the client request the number of permits required
         * to get from the "new-permits-threshold" to "initial-nr-of-permits".
         *
         * @return The additional number of permits send for message streams (events, commands, queries) when
         * application is ready for more messages.
         */
        public Integer getNrOfNewPermits() {
            if (nrOfNewPermits == null || nrOfNewPermits <= 0) {
                return getPermits() - getNewPermitsThreshold();
            }
            return nrOfNewPermits;
        }

        /**
         * Sets the additional number of permits send for message streams (events, commands, queries) when application
         * is ready for more messages.
         * <p>
         * A value of {@code null}, 0, and negative values will have the client request the number of permits required
         * to get from the "new-permits-threshold" to "initial-nr-of-permits".
         *
         * @param nrOfNewPermits The additional number of permits send for message streams (events, commands, queries)
         *                       when application is ready for more messages.
         */
        public void setNrOfNewPermits(Integer nrOfNewPermits) {
            this.nrOfNewPermits = nrOfNewPermits;
        }

        /**
         * The threshold at which the application sends new permits to server.
         * <p>
         * A value of {@code null}, 0, and negative values will have the threshold set to 50% of
         * {@link #getPermits() the initial number of permits}.
         *
         * @return The threshold at which the application sends new permits to server.
         */
        public Integer getNewPermitsThreshold() {
            if (newPermitsThreshold == null || newPermitsThreshold <= 0) {
                return getPermits() / 2;
            }
            return newPermitsThreshold;
        }

        /**
         * Sets the threshold at which the application sends new permits to server.
         * <p>
         * A value of {@code null}, 0, and negative values will have the threshold set to 50% of
         * {@link #getPermits() the initial number of permits}.
         *
         * @param newPermitsThreshold The threshold at which the application sends new permits to server.
         */
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
         * The interval between consecutive heartbeat message sent in milliseconds. Defaults to {@code 10_000}
         * milliseconds.
         */
        private long interval = DEFAULT_INTERVAL;

        /**
         * The time window within which a response is expected in milliseconds. The connection times out if no response
         * is returned within this window. Defaults to {@code 7_500} milliseconds.
         */
        private long timeout = DEFAULT_TIMEOUT;

        /**
         * Indication whether heartbeat messages between a client and Axon Server are enabled. When enabled, the
         * connection will be abandoned if a heartbeat message response <b>is not</b> returned in a timely manner.
         * Defaults to {@code true}.
         *
         * @return Indication whether heartbeat messages between a client and Axon Server are enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets the indication whether heartbeat messages between a client and Axon Server are enabled. When enabled,
         * the connection will be abandoned if a heartbeat message response <b>is not</b> returned in a timely manner.
         * Defaults to {@code true}.
         *
         * @param enabled The Indication whether heartbeat messages between a client and Axon Server are enabled.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * The interval between consecutive heartbeat message sent in milliseconds. Defaults to {@code 10_000}
         * milliseconds.
         *
         * @return The interval between consecutive heartbeat message sent in milliseconds.
         */
        public long getInterval() {
            return interval;
        }

        /**
         * Sets the interval between consecutive heartbeat message sent in milliseconds. Defaults to {@code 10_000}
         * milliseconds.
         *
         * @param interval The interval between consecutive heartbeat message sent in milliseconds.
         */
        public void setInterval(long interval) {
            this.interval = interval;
        }

        /**
         * The time window within which a response is expected in milliseconds. The connection times out if no response
         * is returned within this window. Defaults to {@code 7_500} milliseconds.
         *
         * @return The time window within which a response is expected in milliseconds.
         */
        public long getTimeout() {
            return timeout;
        }

        /**
         * Sets the time window within which a response is expected in milliseconds. The connection times out if no
         * response is returned within this window. Defaults to {@code 7_500} milliseconds.
         *
         * @param timeout The time window within which a response is expected in milliseconds.
         */
        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
    }

    public static class Eventhandling {

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
             * {@link StreamingEventProcessor StreamingEventProcessors}, as only {@code StreamingEventProcessors} are
             * capable of splitting the event handling load in segments.
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
            // The method name is 'awkward' as otherwise property files cannot resolve the field.
            public boolean isAutomaticBalancing() {
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
         * Whether (automatic) configuration of the Axon Server Event Store is enabled. When {@code false}, the event
         * store will not be implicitly be configured. Defaults to {@code true}.
         * <p>
         * Note that this setting will only affect automatic configuration by Application Containers (such as Spring).
         */
        private boolean enabled = true;

        /**
         * An indicator whether (automatic) configuration of the Axon Server Event Store is enabled. When {@code false},
         * the event store will not be implicitly be configured. Defaults to {@code true}.
         * <p>
         * Note that this setting will only affect automatic configuration by Application Containers (such as Spring).
         *
         * @return An indicator whether (automatic) configuration of the Axon Server Event Store is enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets the indicator whether (automatic) configuration of the Axon Server Event Store is enabled. When
         * {@code false}, the event store will not be implicitly be configured. Defaults to {@code true}.
         * <p>
         * Note that this setting will only affect automatic configuration by Application Containers (such as Spring).
         *
         * @param enabled The indicator whether (automatic) configuration of the Axon Server Event Store is enabled.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @SuppressWarnings("unused")
    public static class Builder {

        private final AxonServerConfiguration instance;

        public Builder() {
            instance = new AxonServerConfiguration();
            instance.permits = 5000;
            instance.nrOfNewPermits = null;
            instance.newPermitsThreshold = null;
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

        public Builder flowControl(int permits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.permits = permits;
            instance.nrOfNewPermits = nrOfNewPermits;
            instance.newPermitsThreshold = newPermitsThreshold;
            return this;
        }

        public Builder commandFlowControl(int permits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.setCommandFlowControl(new FlowControlConfiguration(permits, nrOfNewPermits, newPermitsThreshold));
            return this;
        }

        public Builder queryFlowControl(int permits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.setQueryFlowControl(new FlowControlConfiguration(permits, nrOfNewPermits, newPermitsThreshold));
            return this;
        }

        public Builder eventFlowControl(int permits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.setEventFlowControl(new FlowControlConfiguration(permits, nrOfNewPermits, newPermitsThreshold));
            return this;
        }

        /**
         * @deprecated Through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java
         * Connector</a> project.
         */
        @Deprecated
        public Builder setEventSecretKey(String key) {
            instance.setEventSecretKey(key);
            return this;
        }

        /**
         * @deprecated Through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java
         * Connector</a> project.
         */
        @Deprecated
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

        /**
         * @deprecated Through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">Axon Server Java
         * Connector</a> project, which enables the download message in absence of configured servers.
         */
        @Deprecated
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

    public static class PersistentStreamSettings {

        private static final String DEFAULT_SEQUENCING_POLICY = PersistentStreamSequencingPolicyProvider.SEQUENTIAL_PER_AGGREGATE_POLICY;

        /**
         * The number of segments for the persistent stream if it needs to be created. Defaults to 1.
         */
        private int initialSegmentCount = 1;

        /**
         * The number of threads used to process tasks (e.g. event handling) for the persistent stream. Defaults to 1.
         */
        private int threadCount = 1;

        /**
         * The sequencing policy to use for the persistent stream.
         * <p>
         * Supported sequencing policies are:
         * <ul>
         *     <li>{@link PersistentStreamSequencingPolicyProvider#SEQUENTIAL_PER_AGGREGATE_POLICY} (default)</li>
         *     <li>{@link PersistentStreamSequencingPolicyProvider#FULL_CONCURRENCY_POLICY}</li>
         *     <li>{@link PersistentStreamSequencingPolicyProvider#SEQUENTIAL_POLICY}</li>
         *     <li>{@link PersistentStreamSequencingPolicyProvider#PROPERTY_SEQUENCING_POLICY}</li>
         *     <li>{@link PersistentStreamSequencingPolicyProvider#META_DATA_SEQUENCING_POLICY}</li>
         * </ul>
         */
        private String sequencingPolicy = DEFAULT_SEQUENCING_POLICY;

        /**
         * Parameters specified for the sequencing policy.
         */
        private List<String> sequencingPolicyParameters = new LinkedList<>();

        /**
         * Expression to filter out events in a persistent stream, expecting the Axon Server Query Language as its
         * syntax.
         * <p>
         * Note that it is <b>not possible</b> to change the filter once the persistent stream has been created! When
         * doing so, Axon Server will typically throw an {@code AXONIQ-0001} exception.
         */
        private String filter;

        /**
         * The name for the persistent stream.
         */
        private String name;

        /**
         * The maximum number of events to process in a single transaction when reading a persistent stream. Defaults to
         * 1.
         */
        private int batchSize = 1;

        /**
         * The initial token for the persistent stream. This can be a global sequence in the event store or keyword
         * {@code "HEAD"} or {@code "TAIL"}. Defaults to {@code "TAIL"}.
         */
        private String initialPosition = "TAIL";

        /**
         * The number of segments for the persistent stream if it needs to be created.
         *
         * @return the number of segments for the persistent stream if it needs to be created
         */
        public int getInitialSegmentCount() {
            return initialSegmentCount;
        }

        /**
         * Sets the number of segments for the persistent stream if it needs to be created.
         *
         * @param initialSegmentCount the number of segments
         */
        public void setInitialSegmentCount(int initialSegmentCount) {
            this.initialSegmentCount = initialSegmentCount;
        }

        /**
         * The number of threads used to process tasks (e.g. event handling) for the persistent stream. Defaults to 1.
         *
         * @return The number of threads used to process tasks (e.g. event handling) for the persistent stream.
         */
        public int getThreadCount() {
            return threadCount;
        }

        /**
         * Sets the number of threads used to process tasks (e.g. event handling) for the persistent stream.
         *
         * @param threadCount The number of threads used to process tasks (e.g. event handling) for the persistent
         *                    stream.
         */
        public void setThreadCount(int threadCount) {
            this.threadCount = threadCount;
        }

        /**
         * The sequencing policy to use for the persistent stream.
         *
         * @return the sequencing policy name
         */
        public String getSequencingPolicy() {
            return sequencingPolicy;
        }

        /**
         * Sets the sequencing policy to use for the persistent stream.
         * <p>
         * Supported sequencing policies are:
         * <ul>
         *     <li>SequentialPerAggregatePolicy (default)</li>
         *     <li>FullConcurrencyPolicy</li>
         *     <li>SequentialPolicy</li>
         *     <li>PropertySequencingPolicy</li>
         *     <li>MetaDataSequencingPolicy</li>
         * </ul>
         * </p>
         * <p>This value is only used for creating the persistent stream.</p>
         *
         * @param sequencingPolicy The sequencing policy name.
         */
        public void setSequencingPolicy(String sequencingPolicy) {
            this.sequencingPolicy = sequencingPolicy;
        }

        /**
         * Parameters specified for the sequencing policy.
         *
         * @return A list of parameters specified for the sequencing policy.
         */
        public List<String> getSequencingPolicyParameters() {
            return sequencingPolicyParameters;
        }

        /**
         * Sets the parameters specified for the sequencing policy.
         * <p>
         * The <em>PropertySequencingPolicy</em> and <em>MetaDataSequencingPolicy</em> require parameters.
         * </p>
         * <p>This value is only used for creating the persistent stream.</p>
         *
         * @param sequencingPolicyParameters A list of parameters specified for the sequencing policy.
         */
        public void setSequencingPolicyParameters(List<String> sequencingPolicyParameters) {
            this.sequencingPolicyParameters = sequencingPolicyParameters;
        }

        /**
         * Expression to filter out events in a persistent stream, using Axon Server Query Language as its syntax.
         * <p>
         * Note that it is <b>not possible</b> to change the filter once the persistent stream has been created! When
         * doing so, Axon Server will typically throw an {@code AXONIQ-0001} exception.
         *
         * @return The filter expression.
         */
        public String getFilter() {
            return filter;
        }

        /**
         * Sets the expression to filter out events in a persistent stream, expecting the Axon Server Query Language as
         * its syntax.
         * <p>
         * Note that it is <b>not possible</b> to change the filter once the persistent stream has been created! When
         * doing so, Axon Server will typically throw an {@code AXONIQ-0001} exception.
         *
         * @param filter The filter expression.
         */
        public void setFilter(String filter) {
            this.filter = filter;
        }

        /**
         * The name for the persistent stream.
         *
         * @return The given name for a persistent stream.
         */
        public String getName() {
            return name;
        }

        /**
         * Assigns a name for the persistent stream.
         * <p>This value is only used for creating the persistent stream.</p>
         *
         * @param name The given name for a persistent stream.
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * The maximum number of events to process in a single transaction when reading a persistent stream.
         *
         * @return The batch size.
         */
        public int getBatchSize() {
            return batchSize;
        }

        /**
         * Sets the maximum number of events to process in a single transaction when reading a persistent stream. The
         * default value is 1.
         *
         * @param batchSize The batch size.
         */
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        /**
         * The initial token position for the persistent stream. This can be a global sequence in the event store or the
         * keywords {@code "HEAD"} or {@code "TAIL"} for a respective position at the head or tail of the stream.
         *
         * @return The initial token position.
         */
        public String getInitialPosition() {
            return initialPosition;
        }

        /**
         * Sets the initial token for the persistent stream. The default value is 0, starting the persistent stream from
         * the first event in the event store.
         * <p>You can use values {@code "HEAD"} or {@code "TAIL"} to start from the head or tail of the event
         * stream.</p>
         * <p>This value is only used for creating the persistent stream.</p>
         *
         * @param initialPosition The initial token position.
         */
        public void setInitialPosition(String initialPosition) {
            this.initialPosition = initialPosition;
        }
    }
}
