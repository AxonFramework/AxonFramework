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
import org.axonframework.common.AxonConfigurationException;

import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * RoutingStrategy implementation that uses the value in the {@link org.axonframework.messaging.MetaData} of a {@link
 * CommandMessage} assigned to a given key. The value's {@code toString()} is used to convert the {@code MetaData} value
 * to a String.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaDataRoutingStrategy extends AbstractRoutingStrategy {

    private final String metaDataKey;

    /**
     * Instantiate a Builder to be able to create a {@link MetaDataRoutingStrategy}.
     * <p>
     * The {@code fallbackRoutingStrategy} to {@link UnresolvedRoutingKeyPolicy#RANDOM_KEY}. The {@code metaDataKey} is
     * a <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link MetaDataRoutingStrategy}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link MetaDataRoutingStrategy} based on the fields contained in the give {@code builder}.
     * <p>
     * Will assert that the {@code metaDataKey} is not an empty {@link String} or {@code null} and will throw an {@link
     * AxonConfigurationException} if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MetaDataRoutingStrategy} instance
     */
    protected MetaDataRoutingStrategy(Builder builder) {
        super(builder.fallbackRoutingStrategy);
        builder.validate();
        this.metaDataKey = builder.metaDataKey;
    }

    @Override
    protected String doResolveRoutingKey(@Nonnull CommandMessage<?> command) {
        Object value = command.getMetaData().get(metaDataKey);
        return value == null ? null : value.toString();
    }

    /**
     * Builder class to instantiate a {@link AnnotationRoutingStrategy}.
     * <p>
     * The {@code fallbackRoutingStrategy} to {@link UnresolvedRoutingKeyPolicy#RANDOM_KEY}. The {@code metaDataKey} is
     * a <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private String metaDataKey;
        private RoutingStrategy fallbackRoutingStrategy = UnresolvedRoutingKeyPolicy.RANDOM_KEY;

        /**
         * Sets the fallback {@link RoutingStrategy} to use when the intended routing key resolution was unsuccessful.
         * Defaults to a {@link UnresolvedRoutingKeyPolicy#RANDOM_KEY}
         *
         * @param fallbackRoutingStrategy a {@link RoutingStrategy} used as the fallback whenever the intended routing
         *                                key resolution was unsuccessful
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder fallbackRoutingStrategy(RoutingStrategy fallbackRoutingStrategy) {
            assertNonNull(fallbackRoutingStrategy, "Fallback RoutingStrategy may not be null");
            this.fallbackRoutingStrategy = fallbackRoutingStrategy;
            return this;
        }

        /**
         * Sets the {@code metaDataKey} searched for by this routing strategy on a {@link CommandMessage}'s {@link
         * org.axonframework.messaging.MetaData} to base the routing key on.
         *
         * @param metaDataKey a {@link String} to search for in a {@link CommandMessage}'s {@link
         *                    org.axonframework.messaging.MetaData} to base the routing key on
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder metaDataKey(String metaDataKey) {
            assertNonEmpty(metaDataKey, "A metaDataKey may not be null or empty");
            this.metaDataKey = metaDataKey;
            return this;
        }

        /**
         * Initializes a {@link MetaDataRoutingStrategy} implementation as specified through this Builder.
         *
         * @return a {@link MetaDataRoutingStrategy} implementation as specified through this Builder
         */
        public MetaDataRoutingStrategy build() {
            return new MetaDataRoutingStrategy(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonEmpty(metaDataKey, "The metaDataKey is a hard requirement and should be provided");
        }
    }
}
