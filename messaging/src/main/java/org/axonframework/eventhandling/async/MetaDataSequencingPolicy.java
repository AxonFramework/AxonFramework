/*
 * Copyright (c) 2010-2021. Axon Framework
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

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the event message metaData based
 * on a given metaData key.
 *
 * @author Lucas Campos
 * @since 4.6.0
 */
public class MetaDataSequencingPolicy implements SequencingPolicy<EventMessage<?>> {

    private final String metaData;

    /**
     * Instantiate a {@link MetaDataSequencingPolicy} based on the fields contained in the {@link
     * MetaDataSequencingPolicy.Builder}.
     * <p>
     * Will assert that the {@code metaData} is not {@code null} and will throw an {@link AxonConfigurationException} if
     * this is the case.
     *
     * @param builder the {@link MetaDataSequencingPolicy.Builder} used to instantiate a {@link
     *                MetaDataSequencingPolicy} instance
     */
    protected MetaDataSequencingPolicy(Builder builder) {
        builder.validate();
        this.metaData = builder.metaData;
    }

    @Override
    public Object getSequenceIdentifierFor(EventMessage<?> event) {
        return event.getMetaData().getOrDefault(metaData, event.getIdentifier());
    }

    /**
     * Instantiate a Builder to be able to create a {@link MetaDataSequencingPolicy}.
     * <p>
     * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     * <li>The {@code metaData} key to be used as a lookup for the property to be used as the Sequence Policy.</li>
     * </ul>
     *
     * @return a Builder to be able to create a {@link MetaDataSequencingPolicy}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class to instantiate a {@link MetaDataSequencingPolicy}.
     * <p>
     * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
     * <ul>
     * <li>The {@code metaData} key to be used as a lookup for the property to be used as the Sequence Policy.</li>
     * </ul>
     */
    public static final class Builder {

        private String metaData;

        private Builder() {
        }

        /**
         * Defines the metaData key to be used as a lookup for the property to be used as the Sequence Policy.
         *
         * @param metaData key to be used as a lookup for the property to be used as the Sequence Policy
         * @return The current Builder instance, for fluent interfacing
         */
        public Builder metaData(String metaData) {
            this.metaData = metaData;
            return this;
        }

        /**
         * Initializes a {@link MetaDataSequencingPolicy} as specified through this Builder.
         *
         * @return a {@link MetaDataSequencingPolicy} as specified through this Builder
         */
        public MetaDataSequencingPolicy build() {
            return new MetaDataSequencingPolicy(this);
        }

        private void validate() {
            assertNonNull(metaData, "MetaData value may not be null");
        }
    }
}
