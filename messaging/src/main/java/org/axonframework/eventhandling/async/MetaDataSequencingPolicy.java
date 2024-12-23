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

package org.axonframework.eventhandling.async;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;

import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the {@link EventMessage}'s
 * {@link org.axonframework.messaging.MetaData}, based on a given {@code metaDataKey}. In the absence of the given
 * {@code metaDataKey} on the {@link org.axonframework.messaging.MetaData}, the {@link EventMessage#getIdentifier()} is
 * used.
 *
 * @author Lucas Campos
 * @since 4.6.0
 */
public class MetaDataSequencingPolicy implements SequencingPolicy<EventMessage<?>> {

    private final String metaDataKey;

    /**
     * Instantiate a {@link MetaDataSequencingPolicy} based on the fields contained in the
     * {@link MetaDataSequencingPolicy.Builder}.
     * <p>
     * Will assert that the {@code metaDataKey} is not {@code null} and will throw an {@link AxonConfigurationException}
     * if this is the case.
     *
     * @param builder The {@link MetaDataSequencingPolicy.Builder} used to instantiate a
     *                {@link MetaDataSequencingPolicy} instance.
     */
    protected MetaDataSequencingPolicy(Builder builder) {
        builder.validate();
        this.metaDataKey = builder.metaDataKey;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MetaDataSequencingPolicy}.
     * <p>
     * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     * <li>The {@code metaDataKey} key to be used as a lookup for the property to be used as the Sequence Policy.</li>
     * </ul>
     *
     * @return A Builder to be able to create a {@link MetaDataSequencingPolicy}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Object getSequenceIdentifierFor(@Nonnull EventMessage<?> event) {
        return event.getMetaData()
                    .getOrDefault(metaDataKey, event.getIdentifier());
    }

    /**
     * Builder class to instantiate a {@link MetaDataSequencingPolicy}.
     * <p>
     * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     * <li>The {@code metaDataKey} key to be used as a lookup for the property to be used as the Sequence Policy.</li>
     * </ul>
     */
    public static class Builder {

        private String metaDataKey;

        private Builder() {
        }

        /**
         * Defines the metaDataKey key to be used as a lookup for the property to be used as the Sequence Policy.
         *
         * @param metaDataKey Key to be used as a lookup for the property to be used as the Sequence Policy.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder metaDataKey(String metaDataKey) {
            this.metaDataKey = metaDataKey;
            return this;
        }

        /**
         * Initializes a {@link MetaDataSequencingPolicy} as specified through this Builder.
         *
         * @return A {@link MetaDataSequencingPolicy} as specified through this Builder.
         */
        public MetaDataSequencingPolicy build() {
            return new MetaDataSequencingPolicy(this);
        }

        protected void validate() {
            assertNonNull(metaDataKey, "MetaDataKey value may not be null");
        }
    }
}
