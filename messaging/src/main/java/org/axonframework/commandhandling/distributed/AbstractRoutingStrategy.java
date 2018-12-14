/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.common.Assert;

import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;

/**
 * Abstract implementation of the RoutingStrategy interface that uses a policy to prescribe what happens when a routing
 * cannot be resolved.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractRoutingStrategy implements RoutingStrategy {

    private static final String STATIC_ROUTING_KEY = "unresolved";

    private final UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy;
    private final AtomicLong counter = new AtomicLong(0);

    /**
     * Initializes the strategy using given {@code unresolvedRoutingKeyPolicy} prescribing what happens when a
     * routing key cannot be resolved.
     *
     * @param unresolvedRoutingKeyPolicy The policy for unresolved routing keys.
     */
    public AbstractRoutingStrategy(UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy) {
        Assert.notNull(unresolvedRoutingKeyPolicy, () -> "unresolvedRoutingKeyPolicy may not be null");
        this.unresolvedRoutingKeyPolicy = unresolvedRoutingKeyPolicy;
    }

    @Override
    public String getRoutingKey(CommandMessage<?> command) {
        String routingKey = doResolveRoutingKey(command);
        if (routingKey == null) {
            switch (unresolvedRoutingKeyPolicy) {
                case ERROR:
                    throw new CommandDispatchException(format("The command [%s] does not contain a routing key.",
                                                              command.getCommandName()));
                case RANDOM_KEY:
                    return Long.toHexString(counter.getAndIncrement());
                case STATIC_KEY:
                    return STATIC_ROUTING_KEY;
                default:
                    throw new IllegalStateException("The configured UnresolvedRoutingPolicy of "
                                                            + unresolvedRoutingKeyPolicy.name() + " is not supported.");
            }
        }
        return routingKey;
    }

    /**
     * Resolve the Routing Key for the given {@code command}.
     *
     * @param command The command to resolve the routing key for
     * @return the String representing the Routing Key, or {@code null} if unresolved.
     */
    protected abstract String doResolveRoutingKey(CommandMessage<?> command);
}
