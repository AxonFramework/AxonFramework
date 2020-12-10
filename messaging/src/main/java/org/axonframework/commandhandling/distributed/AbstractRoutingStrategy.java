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
import org.axonframework.common.AxonConfigurationException;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of the {@link RoutingStrategy} interface that includes a fallback {@code RoutingStrategy}
 * which prescribes what happens when routing cannot be resolved by this implementation.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractRoutingStrategy<B extends RoutingStrategy> implements RoutingStrategy {

    private final RoutingStrategy fallbackRoutingStrategy;

    /**
     * Instantiate a {@link AbstractRoutingStrategy} based on the fields contained in the given {@code builder}
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractRoutingStrategy} instance
     */
    protected AbstractRoutingStrategy(Builder<B> builder) {
        builder.validate();
        this.fallbackRoutingStrategy = builder.fallbackRoutingStrategy;
    }

    /**
     * Initializes the strategy using given {@link UnresolvedRoutingKeyPolicy} prescribing the fallback approach when
     * this implementation cannot resolve a routing key.
     *
     * @param fallbackRoutingStrategy the fallback routing to use whenever this {@link RoutingStrategy} doesn't succeed
     * @deprecated in favor of the {@link #AbstractRoutingStrategy(Builder)}
     */
    @Deprecated
    public AbstractRoutingStrategy(UnresolvedRoutingKeyPolicy fallbackRoutingStrategy) {
        assertNonNull(fallbackRoutingStrategy, "Fallback RoutingStrategy may not be null");
        this.fallbackRoutingStrategy = fallbackRoutingStrategy;
    }

    @Override
    public String getRoutingKey(CommandMessage<?> command) {
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
    protected abstract String doResolveRoutingKey(CommandMessage<?> command);

    /**
     * Builder class to instantiate a {@link AbstractRoutingStrategy}.
     * <p>
     * The fallback {@link RoutingStrategy} is defaulted to a {@link UnresolvedRoutingKeyPolicy#RANDOM_KEY}.
     *
     * @param <B> generic defining the type of {@link RoutingStrategy} this builder will create
     */
    protected static abstract class Builder<B extends RoutingStrategy> {

        private RoutingStrategy fallbackRoutingStrategy = UnresolvedRoutingKeyPolicy.RANDOM_KEY;

        /**
         * Sets the fallback {@link RoutingStrategy} to use when the intended routing key resolution was unsuccessful.
         * Defaults to a {@link UnresolvedRoutingKeyPolicy#RANDOM_KEY}
         *
         * @param fallbackRoutingStrategy a {@link RoutingStrategy} used as the fallback whenever the intended routing
         *                                key resolution was unsuccessful
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<B> fallbackRoutingStrategy(RoutingStrategy fallbackRoutingStrategy) {
            assertNonNull(fallbackRoutingStrategy, "Fallback RoutingStrategy may not be null");
            this.fallbackRoutingStrategy = fallbackRoutingStrategy;
            return this;
        }

        /**
         * Initializes a {@link RoutingStrategy} implementation as specified through this Builder.
         *
         * @return a {@link RoutingStrategy} implementation as specified through this Builder
         */
        public abstract B build();

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected abstract void validate();
    }
}
