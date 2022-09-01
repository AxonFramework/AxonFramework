/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;

import java.util.function.Function;
import javax.annotation.Nonnull;

import static java.lang.String.format;

/**
 * Set of simple {@link RoutingStrategy} implementations. Could for example be used as fallback solutions when another
 * {@code RoutingStrategy} is unable to resolve the routing key.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public enum UnresolvedRoutingKeyPolicy implements RoutingStrategy {

    /**
     * A {@link RoutingStrategy} which always throws a {@link CommandDispatchException} regardless of the {@link
     * CommandMessage} received. Only feasible as a fallback solution which should straight out fail if the intended
     * policy is unable to resolve a routing key.
     * <p>
     * Note that when the routing key is based on static content in the {@code CommandMessage}, the exception raised
     * should extend from {@link org.axonframework.common.AxonNonTransientException} to indicate that retries do not
     * have a chance to succeed.
     *
     * @see org.axonframework.commandhandling.gateway.RetryScheduler
     */
    ERROR(command -> {
        throw new CommandDispatchException(format(
                "The command [%s] does not contain a routing key.", command.getCommandName()
        ));
    }),

    /**
     * Policy that indicates a random key is to be returned when no Routing Key can be found for a Command Message. This
     * effectively means the Command Message is routed to a random segment.
     * <p>
     * Although not required to be fully random, implementations are required to return a different key for each
     * incoming command. Multiple invocations for the same command message may return the same value, but are not
     * required to do so.
     */
    RANDOM_KEY(command -> Double.toHexString(Math.random())),

    /**
     * Policy that indicates a fixed key ("unresolved") should be returned when no Routing Key can be found for a
     * Command Message. This effectively means all Command Messages with unresolved routing keys are routed to a the
     * same segment. The load of that segment may therefore not match the load factor.
     */
    STATIC_KEY(command -> "unresolved");

    private final Function<CommandMessage<?>, String> routingKeyResolver;

    UnresolvedRoutingKeyPolicy(Function<CommandMessage<?>, String> routingKeyResolver) {
        this.routingKeyResolver = routingKeyResolver;
    }

    @Override
    public String getRoutingKey(@Nonnull CommandMessage<?> command) {
        return routingKeyResolver.apply(command);
    }
}
