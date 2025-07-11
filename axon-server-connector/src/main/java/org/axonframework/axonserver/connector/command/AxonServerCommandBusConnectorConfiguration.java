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

package org.axonframework.axonserver.connector.command;

import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.distributed.CommandPriorityResolver;
import org.axonframework.commandhandling.distributed.RoutingStrategy;

/**
 * Configuration for the {@link AxonServerCommandBusConnector}. It allows setting a {@link RoutingStrategy} and a
 * {@link CommandPriorityResolver} to be used by the connector. All settings are optional.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public record AxonServerCommandBusConnectorConfiguration(
        @Nullable RoutingStrategy routingStrategy,
        @Nullable CommandPriorityResolver commandPriorityResolver
) {

    /**
     * Initializes a default {@code AxonServerCommandBusConnectorConfiguration}.
     */
    public AxonServerCommandBusConnectorConfiguration() {
        this(null, null);
    }

    /**
     * Sets the {@link RoutingStrategy} to be used by the {@link AxonServerCommandBusConnector}. If no
     * {@link RoutingStrategy} is provided, no routing key will be set on commands sent to Axon Server, and commands
     * will be distributed randomly.
     *
     * @param routingStrategy The {@link RoutingStrategy} to use for routing commands. If {@code null}, no routing key
     *                        will be set.
     * @return A new {@code AxonServerCommandBusConnectorConfiguration} with the given routing strategy.
     */
    public AxonServerCommandBusConnectorConfiguration withRoutingStrategy(@Nullable RoutingStrategy routingStrategy) {
        return new AxonServerCommandBusConnectorConfiguration(routingStrategy, commandPriorityResolver);
    }

    /**
     * Sets the {@link CommandPriorityResolver} to be used by the {@link AxonServerCommandBusConnector}. If no
     * {@link CommandPriorityResolver} is provided, commands will be sent without a priority.
     *
     * @param commandPriorityResolver The {@link CommandPriorityResolver} to use for determining command priorities.
     * @return A new {@code AxonServerCommandBusConnectorConfiguration} with the given command priority resolver.
     */
    public AxonServerCommandBusConnectorConfiguration withCommandPriorityResolver(
            @Nullable CommandPriorityResolver commandPriorityResolver
    ) {
        return new AxonServerCommandBusConnectorConfiguration(routingStrategy, commandPriorityResolver);
    }
}
