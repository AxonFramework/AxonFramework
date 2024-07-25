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

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.axoniq.axonserver.connector.impl.ContextConnection;
import io.axoniq.axonserver.connector.impl.ServerAddress;
import io.axoniq.axonserver.grpc.control.NodeInfo;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.config.TagsConfiguration;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.net.ssl.SSLException;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * The component which manages all the connections which an Axon client can establish with an Axon Server instance. Does
 * so by creating {@link Channel}s per context and providing them as the means to dispatch/receive messages.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerConnectionManager implements Lifecycle, ConnectionManager {

    private static final int DEFAULT_GRPC_PORT = 8124;

    private final Map<String, AxonServerConnection> connections = new ConcurrentHashMap<>();
    private final AxonServerConnectionFactory connectionFactory;
    private final String defaultContext;
    private final boolean heartbeatEnabled;
    private final long heartbeatInterval;
    private final long heartbeatTimeout;

    /**
     * Instantiate a {@link AxonServerConnectionManager} based on the fields contained in the {@link Builder}, using the
     * given {@code connectionFactory} to obtain connections to AxonServer.
     *
     * @param builder           the {@link Builder} used to instantiate a {@link AxonServerConnectionManager} instance
     * @param connectionFactory a configured instance of the AxonServerConnectionFactory
     */
    protected AxonServerConnectionManager(Builder builder, AxonServerConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        this.defaultContext = builder.axonServerConfiguration.getContext();
        AxonServerConfiguration.HeartbeatConfiguration heartbeatConfig =
                builder.axonServerConfiguration.getHeartbeat();
        this.heartbeatEnabled = heartbeatConfig.isEnabled();
        this.heartbeatInterval = heartbeatConfig.getInterval();
        this.heartbeatTimeout = heartbeatConfig.getTimeout();
    }

    /**
     * Instantiate a Builder to be able to create an {@link AxonServerConnectionManager}.
     * <p>
     * The {@link Builder#routingServers(String) routingServers} default to {@code "localhost:8024"} and the
     * {@link TagsConfiguration} is defaulted to {@link TagsConfiguration#TagsConfiguration()}. The
     * {@link AxonServerConfiguration} is a <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link AxonServerConnectionManager}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.INSTRUCTION_COMPONENTS, this::start);
        lifecycle.onShutdown(Phase.EXTERNAL_CONNECTIONS, this::shutdown);
    }

    /**
     * Starts the {@link AxonServerConnectionManager}. Will enable heartbeat messages to be send to the connected Axon
     * Server instance in the {@link Phase#INSTRUCTION_COMPONENTS} phase, if this has been enabled through the
     * {@link AxonServerConfiguration.HeartbeatConfiguration#isEnabled()}.
     */
    public void start() {
        if (heartbeatEnabled) {
            // for backwards compatibility. Starting the ConnectionManager with heartbeat enabled should eagerly create
            // a connection to Axon Server
            getConnection();
        }
    }

    /**
     * Retrieves the {@link AxonServerConnection} used for the default context of this application.
     *
     * @return the {@link AxonServerConnection} used for the default context of this application
     */
    public AxonServerConnection getConnection() {
        return getConnection(getDefaultContext());
    }

    /**
     * Retrieves the {@link AxonServerConnection} used for the given {@code context} of this application.
     *
     * @param context the context for which to retrieve an {@link AxonServerConnection}
     * @return the {@link AxonServerConnection} used for the given {@code context} of this application.
     */
    public AxonServerConnection getConnection(String context) {
        return connections.computeIfAbsent(context, this::createConnection);
    }

    private AxonServerConnection createConnection(String context) {
        AxonServerConnection connection = connectionFactory.connect(context);
        if (heartbeatEnabled) {
            connection.controlChannel()
                      .enableHeartbeat(heartbeatInterval, heartbeatTimeout, TimeUnit.MILLISECONDS);
        }

        return connection;
    }

    /**
     * Returns {@code true} if a gRPC channel for the specific context is opened between client and AxonServer.
     *
     * @param context the (Bounded) Context for which is verified the AxonServer connection through the gRPC
     *                channel
     * @return if the gRPC channel is opened, false otherwise
     */
    public boolean isConnected(String context) {
        AxonServerConnection channel = connections.get(context);
        return channel != null && channel.isConnected();
    }

    /**
     * Stops the Connection Manager, closing any active connections and preventing new connections from being created.
     * This shutdown operation is performed in the {@link Phase#EXTERNAL_CONNECTIONS} phase.
     */
    public void shutdown() {
        connectionFactory.shutdown();
        disconnect();
    }

    /**
     * Disconnects any active connection for the given {@code context}, forcing a new connection to be established when
     * one is requested.
     *
     * @param context the context for which the connection must be disconnected
     */
    public void disconnect(String context) {
        AxonServerConnection channel = connections.remove(context);
        if (channel != null) {
            channel.disconnect();
        }
    }

    /**
     * Disconnects any active connections, forcing a new connection to be established when one is requested.
     */
    public void disconnect() {
        connections.forEach((context, channel) -> channel.disconnect());
        connections.clear();
    }

    /**
     * Returns the name of the default context of this application.
     *
     * @return the name of the default context of this application
     */
    public String getDefaultContext() {
        return defaultContext;
    }

    @Deprecated
    public Channel getChannel() {
        return ((ContextConnection) getConnection(defaultContext)).getManagedChannel();
    }

    @Deprecated
    public Channel getChannel(String context) {
        return ((ContextConnection) getConnection(context)).getManagedChannel();
    }

    @Override
    public Map<String, Boolean> connections() {
        return connections.entrySet()
                          .stream()
                          .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().isConnected()));
    }

    /**
     * Builder class to instantiate an {@link AxonServerConnectionManager}.
     * <p>
     * The {@link Builder#routingServers(String) routingServers} default to {@code "localhost:8024"} and the
     * {@link TagsConfiguration} is defaulted to {@link TagsConfiguration#TagsConfiguration()}. The
     * {@link AxonServerConfiguration} is a <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private String routingServers = "localhost:8024";
        private AxonServerConfiguration axonServerConfiguration;
        private TagsConfiguration tagsConfiguration = new TagsConfiguration();
        private UnaryOperator<ManagedChannelBuilder<?>> channelCustomization;

        /**
         * Comma separated list of Axon Server locations. Each element is hostname or hostname:grpcPort. Defaults to
         * {@code "localhost:8024"}.
         *
         * @param routingServers Comma separated list of Axon Server locations.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder routingServers(String routingServers) {
            assertNonEmpty(
                    routingServers,
                    "Routing Servers should be a non-empty String of a comma-separated [hostname:grpcPort] entries"
            );
            this.routingServers = routingServers;
            return this;
        }

        /**
         * Sets the {@link AxonServerConfiguration} used to correctly configure connections between Axon clients and
         * Axon Server.
         *
         * @param axonServerConfiguration an {@link AxonServerConfiguration} used to correctly configure the connections
         *                                created by an {@link AxonServerConnectionManager} instance
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder axonServerConfiguration(AxonServerConfiguration axonServerConfiguration) {
            assertNonNull(axonServerConfiguration, "AxonServerConfiguration may not be null");
            this.axonServerConfiguration = axonServerConfiguration;
            return this;
        }

        /**
         * Sets the {@link TagsConfiguration} used to add the tags of this Axon instance as client information when
         * setting up a channel. Can for example be used to introduce labelling of your client applications for correct
         * distribution in an Axon Server cluster. Defaults to {@link TagsConfiguration#TagsConfiguration()}, thus an
         * empty set of tags.
         *
         * @param tagsConfiguration a {@link TagsConfiguration} to add the tags of this Axon instance as client
         *                          information when setting up a channel
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder tagsConfiguration(TagsConfiguration tagsConfiguration) {
            assertNonNull(tagsConfiguration, "TagsConfiguration may not be null");
            this.tagsConfiguration = tagsConfiguration;
            return this;
        }

        /**
         * Registers the given {@code channelCustomization}, which configures the underling
         * {@link ManagedChannelBuilder} used to set up connections to AxonServer.
         * <p>
         * This method may be used in case none of the operations on this Builder provide support for the required
         * feature.
         *
         * @param channelCustomization A function defining the customization to make on the ManagedChannelBuilder
         * @return this builder for further configuration
         */
        public Builder channelCustomizer(UnaryOperator<ManagedChannelBuilder<?>> channelCustomization) {
            this.channelCustomization = channelCustomization;
            return this;
        }

        /**
         * Sets the Axon Framework version resolver used in order to communicate the client version to send to Axon
         * Server.
         *
         * @param axonFrameworkVersionResolver a string supplier that retrieve the current Axon Framework version
         * @return the current Builder instance, for fluent interfacing
         * @deprecated Not ued anymore
         */
        @Deprecated
        public Builder axonFrameworkVersionResolver(Supplier<String> axonFrameworkVersionResolver) {
            return this;
        }

        /**
         * Initializes a {@link AxonServerConnectionManager} as specified through this Builder.
         *
         * @return a {@link AxonServerConnectionManager} as specified through this Builder
         */
        public AxonServerConnectionManager build() {
            validate();

            AxonServerConnectionFactory.Builder builder = AxonServerConnectionFactory.forClient(
                    axonServerConfiguration.getComponentName(), axonServerConfiguration.getClientId()
            );
            List<NodeInfo> nodeInfos = mapToNodeInfos(routingServers);
            if (!nodeInfos.isEmpty()) {
                ServerAddress[] addresses = new ServerAddress[nodeInfos.size()];
                for (int i = 0; i < addresses.length; i++) {
                    NodeInfo routingServer = nodeInfos.get(i);
                    addresses[i] = new ServerAddress(routingServer.getHostName(), routingServer.getGrpcPort());
                }
                builder.routingServers(addresses);
            }

            if (axonServerConfiguration.isSslEnabled()) {
                if (axonServerConfiguration.getCertFile() != null) {
                    try {
                        File certificateFile = new File(axonServerConfiguration.getCertFile());
                        builder.useTransportSecurity(GrpcSslContexts.forClient()
                                                                    .trustManager(certificateFile)
                                                                    .build());
                    } catch (SSLException e) {
                        throw new AxonConfigurationException("Exception configuring Transport Security", e);
                    }
                } else {
                    builder.useTransportSecurity();
                }
            }

            builder.connectTimeout(axonServerConfiguration.getConnectTimeout(), TimeUnit.MILLISECONDS)
                   .reconnectInterval(axonServerConfiguration.getReconnectInterval(), TimeUnit.MILLISECONDS)
                   .forceReconnectViaRoutingServers(axonServerConfiguration.isForceReconnectThroughServers())
                   .threadPoolSize(axonServerConfiguration.getConnectionManagementThreadPoolSize())
                   .commandPermits(axonServerConfiguration.getCommandFlowControl().getPermits())
                   .queryPermits(axonServerConfiguration.getQueryFlowControl().getPermits());

            if (axonServerConfiguration.getToken() != null) {
                builder.token(axonServerConfiguration.getToken());
            }

            tagsConfiguration.getTags().forEach(builder::clientTag);
            if (axonServerConfiguration.getMaxMessageSize() > 0) {
                builder.maxInboundMessageSize(axonServerConfiguration.getMaxMessageSize());
            }
            if (axonServerConfiguration.getKeepAliveTime() > 0) {
                builder.usingKeepAlive(axonServerConfiguration.getKeepAliveTime(),
                                       axonServerConfiguration.getKeepAliveTimeout(),
                                       TimeUnit.MILLISECONDS,
                                       true);
            }
            if (axonServerConfiguration.getProcessorsNotificationRate() > 0) {
                builder.processorInfoUpdateFrequency(axonServerConfiguration.getProcessorsNotificationRate(),
                                                     TimeUnit.MILLISECONDS);
            }

            if (channelCustomization != null) {
                builder.customize(channelCustomization);
            }

            AxonServerConnectionFactory connectionFactory = builder.build();
            return new AxonServerConnectionManager(this, connectionFactory);
        }

        private static List<NodeInfo> mapToNodeInfos(String servers) {
            String[] serverArray = servers.split(",");
            return Arrays.stream(serverArray)
                         .map(server -> {
                             String[] s = server.trim().split(":");
                             return s.length > 1
                                     ? NodeInfo.newBuilder()
                                               .setHostName(s[0])
                                               .setGrpcPort(Integer.parseInt(s[1]))
                                               .build()
                                     : NodeInfo.newBuilder()
                                               .setHostName(s[0])
                                               .setGrpcPort(DEFAULT_GRPC_PORT)
                                               .build();
                         })
                         .collect(Collectors.toList());
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(
                    axonServerConfiguration, "The AxonServerConfiguration is a hard requirement and should be provided"
            );
        }
    }
}
