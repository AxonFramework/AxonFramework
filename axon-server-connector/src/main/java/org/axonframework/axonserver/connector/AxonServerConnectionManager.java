/*
 * Copyright (c) 2010-2020. Axon Framework
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
import io.netty.handler.ssl.SslContextBuilder;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.config.TagsConfiguration;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * The component which manages all the connections which an Axon client can establish with an Axon Server instance. Does
 * so by creating {@link Channel}s per context and providing them as the means to dispatch/receive messages.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerConnectionManager {

    private final Map<String, AxonServerConnection> connections = new ConcurrentHashMap<>();
    private final AxonServerConnectionFactory connectionFactory;
    private final String defaultContext;

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
    }

    /**
     * Instantiate a Builder to be able to create an {@link AxonServerConnectionManager}.
     * <p>
     * The {@link TagsConfiguration} is defaulted to {@link TagsConfiguration#TagsConfiguration()} and the {@link
     * ScheduledExecutorService} defaults to an instance using a single thread with an {@link AxonThreadFactory} tied to
     * it. The {@link AxonServerConfiguration} is a <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link AxonServerConnectionManager}
     */
    public static Builder builder() {
        return new Builder();
    }

    public AxonServerConnection getConnection() {
        return getConnection(getDefaultContext());
    }

    public AxonServerConnection getConnection(String context) {
        return connections.computeIfAbsent(context, connectionFactory::connect);
    }

    /**
     * Returns {@code true} if a gRPC channel for the specific context is opened between client and AxonServer.
     *
     * @param context the (Bounded) Context for for which is verified the AxonServer connection through the gRPC
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
    @ShutdownHandler(phase = Phase.EXTERNAL_CONNECTIONS)
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

    /**
     * Builder class to instantiate an {@link AxonServerConnectionManager}.
     * <p>
     * The {@link TagsConfiguration} is defaulted to {@link TagsConfiguration#TagsConfiguration()} and the {@link
     * ScheduledExecutorService} defaults to an instance using a single thread with an {@link AxonThreadFactory} tied to
     * it. The {@link AxonServerConfiguration} is a <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private AxonServerConfiguration axonServerConfiguration;
        private TagsConfiguration tagsConfiguration = new TagsConfiguration();

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
            List<NodeInfo> routingServers = axonServerConfiguration.routingServers();
            if (!routingServers.isEmpty()) {
                ServerAddress[] addresses = new ServerAddress[routingServers.size()];
                for (int i = 0; i < addresses.length; i++) {
                    NodeInfo routingServer = routingServers.get(i);
                    addresses[i] = new ServerAddress(routingServer.getHostName(), routingServer.getGrpcPort());
                }
                builder.routingServers(addresses);
            }

            if (axonServerConfiguration.isSslEnabled()) {
                if (axonServerConfiguration.getCertFile() != null) {
                    try {
                        File certificateFile = new File(axonServerConfiguration.getCertFile());
                        builder.useTransportSecurity(SslContextBuilder.forClient()
                                                                      .trustManager(certificateFile)
                                                                      .build());
                    } catch (SSLException e) {
                        throw new AxonConfigurationException("Exception configuring Transport Security", e);
                    }
                } else {
                    builder.useTransportSecurity();
                }
            }

            builder.connectTimeout(axonServerConfiguration.getConnectTimeout(), TimeUnit.MILLISECONDS);
            if (axonServerConfiguration.getToken() != null) {
                builder.token(axonServerConfiguration.getToken());
            }

            tagsConfiguration.getTags().forEach(builder::clientTag);

            AxonServerConnectionFactory connectionFactory = builder.build();
            return new AxonServerConnectionManager(this, connectionFactory);
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
