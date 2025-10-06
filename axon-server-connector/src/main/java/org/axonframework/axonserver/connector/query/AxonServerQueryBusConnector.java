/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.connector.AxonServerConnection;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.distributed.QueryBusConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AxonServerQueryBusConnector implements QueryBusConnector {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final AxonServerConnection connection;
    private final String clientId;
    private final String componentName;

    private final ShutdownLatch shutdownLatch = new ShutdownLatch();

    /**
     *
     * @param connection
     * @param configuration
     */
    public AxonServerQueryBusConnector(@Nonnull AxonServerConnection connection,
                                       @Nonnull AxonServerConfiguration configuration) {
        this.connection = Objects.requireNonNull(connection, "The AxonServerConnection must not be null.");
        Objects.requireNonNull(configuration, "The AxonServerConfiguration must not be null.");
        this.clientId = configuration.getClientId();
        this.componentName = configuration.getComponentName();
    }

    /**
     * Starts the Axon Server {@link QueryBusConnector} implementation.
     */
    public void start() {
        shutdownLatch.initialize();
        logger.trace("The AxonServerQueryBusConnector started.");
    }

    /**
     * Disconnect the query bus for receiving queries from Axon Server, by unsubscribing all registered query handlers.
     * <p>
     * This shutdown operation is performed in the {@link org.axonframework.lifecycle.Phase#INBOUND_QUERY_CONNECTOR}
     * phase.
     *
     * @return A completable future that resolves once the {@link AxonServerConnection#queryChannel()} has prepared
     * disconnecting.
     */
    public CompletableFuture<Void> disconnect() {
        if (!connection.isConnected()) {
            return FutureUtils.emptyCompletedFuture();
        }
        logger.trace("Disconnecting the AxonServerQueryBusConnector.");
        return connection.queryChannel().prepareDisconnect();
    }

    /**
     * Shutdown the query bus asynchronously for dispatching query to Axon Server.
     * <p>
     * This process will wait for dispatched queries which have not received a response yet. This shutdown operation is
     * performed in the {@link org.axonframework.lifecycle.Phase#OUTBOUND_QUERY_CONNECTORS} phase.
     *
     * @return A completable future which is resolved once all query dispatching activities are completed.
     */
    public CompletableFuture<Void> shutdownDispatching() {
        logger.trace("Shutting down dispatching of AxonServerQueryBusConnector.");
        return shutdownLatch.initiateShutdown();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("connection", connection);
        descriptor.describeProperty("clientId", clientId);
        descriptor.describeProperty("componentName", componentName);
    }
}
