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

package org.axonframework.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the {@link EventMessage}'s
 * {@link org.axonframework.messaging.Metadata}, based on a given {@code metadataKey}. In the absence of the given
 * {@code metadataKey} on the {@link org.axonframework.messaging.Metadata}, the {@link EventMessage#identifier()} is
 * used.
 *
 * @author Lucas Campos
 * @since 4.6.0
 */
public class MetadataSequencingPolicy implements SequencingPolicy {

    private final String metadataKey;

    /**
     * Instantiate a {@link MetadataSequencingPolicy} based on the fields contained in the
     * {@link MetadataSequencingPolicy.Builder}.
     * <p>
     * Will assert that the {@code metadataKey} is not {@code null} and will throw an {@link AxonConfigurationException}
     * if this is the case.
     *
     * @param builder The {@link MetadataSequencingPolicy.Builder} used to instantiate a
     *                {@link MetadataSequencingPolicy} instance.
     */
    protected MetadataSequencingPolicy(Builder builder) {
        builder.validate();
        this.metadataKey = builder.metadataKey;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MetadataSequencingPolicy}.
     * <p>
     * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     * <li>The {@code metadataKey} key to be used as a lookup for the property to be used as the Sequence Policy.</li>
     * </ul>
     *
     * @return A Builder to be able to create a {@link MetadataSequencingPolicy}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        return Optional.ofNullable(
                event.metadata()
                     .getOrDefault(metadataKey, event.identifier())
        );
    }

    /**
     * Builder class to instantiate a {@link MetadataSequencingPolicy}.
     * <p>
     * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     * <li>The {@code metadataKey} key to be used as a lookup for the property to be used as the Sequence Policy.</li>
     * </ul>
     */
    public static class Builder {

        private String metadataKey;

        private Builder() {
        }

        /**
         * Defines the metadataKey key to be used as a lookup for the property to be used as the Sequence Policy.
         *
         * @param metadataKey Key to be used as a lookup for the property to be used as the Sequence Policy.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder metadataKey(String metadataKey) {
            this.metadataKey = metadataKey;
            return this;
        }

        /**
         * Initializes a {@link MetadataSequencingPolicy} as specified through this Builder.
         *
         * @return A {@link MetadataSequencingPolicy} as specified through this Builder.
         */
        public MetadataSequencingPolicy build() {
            return new MetadataSequencingPolicy(this);
        }

        protected void validate() {
            assertNonNull(metadataKey, "MetadataKey value may not be null");
        }
    }
}
