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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;

import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of the {@link RoutingStrategy} interface that includes a fallback {@code RoutingStrategy}
 * which prescribes what happens when routing cannot be resolved by this implementation.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractRoutingStrategy implements RoutingStrategy {

    private final RoutingStrategy fallbackRoutingStrategy;

    /**
     * Initializes the strategy using given {@link UnresolvedRoutingKeyPolicy} prescribing the fallback approach when
     * this implementation cannot resolve a routing key.
     *
     * @param fallbackRoutingStrategy the fallback routing to use whenever this {@link RoutingStrategy} doesn't succeed
     * @deprecated in favor of {@link #AbstractRoutingStrategy(RoutingStrategy)}
     */
    @Deprecated
    public AbstractRoutingStrategy(@Nonnull UnresolvedRoutingKeyPolicy fallbackRoutingStrategy) {
        this((RoutingStrategy) fallbackRoutingStrategy);
    }

    /**
     * Initializes the strategy using given {@link RoutingStrategy} prescribing the fallback approach when this
     * implementation cannot resolve a routing key.
     *
     * @param fallbackRoutingStrategy the fallback routing to use whenever this {@link RoutingStrategy} doesn't succeed
     */
    protected AbstractRoutingStrategy(@Nonnull RoutingStrategy fallbackRoutingStrategy) {
        assertNonNull(fallbackRoutingStrategy, "Fallback RoutingStrategy may not be null");
        this.fallbackRoutingStrategy = fallbackRoutingStrategy;
    }

    @Override
    public String getRoutingKey(@Nonnull CommandMessage<?> command) {
        String routingKey = doResolveRoutingKey(command);
        if (routingKey == null) {
            routingKey = fallbackRoutingStrategy.getRoutingKey(command);
        }
        return routingKey;
    }

    /**
     * Resolve the Routing Key for the given {@code command}.
     *
     * @param command the command to resolve the routing key for
     * @return the String representing the Routing Key, or {@code null} if unresolved
     */
    protected abstract String doResolveRoutingKey(@Nonnull CommandMessage<?> command);
}
