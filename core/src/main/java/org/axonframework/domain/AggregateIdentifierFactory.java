/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.domain;

import java.util.UUID;

/**
 * Factory class that creates AggregateIdentifier instances. Instances can either be created using a random ({@link
 * java.util.UUID}) value, or using an existing value.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AggregateIdentifierFactory {

    private AggregateIdentifierFactory() {
    }

    /**
     * Creates an AggregateIdentifier from the given String <code>identifier</code>.
     *
     * @param identifier The identifier String to create an AggregateIdentifier for
     * @return The AggregateIdentifier representing the given <code>identifier</code>.
     */
    public static AggregateIdentifier fromString(String identifier) {
        return new DefaultAggregateIdentifier(identifier);
    }

    /**
     * Creates an AggregateIdentifier from the given UUID <code>identifier</code>.
     *
     * @param identifier The identifier String to create an AggregateIdentifier for
     * @return The AggregateIdentifier representing the given <code>identifier</code>.
     */
    public static AggregateIdentifier fromUUID(UUID identifier) {
        return fromString(identifier.toString());
    }

    /**
     * Returns an AggregateIdentifier instance backed by a randomly generated UUID value.
     *
     * @return an AggregateIdentifier instance backed by a randomly generated UUID value.
     *
     * @see java.util.UUID
     */
    public static AggregateIdentifier randomIdentifier() {
        return new DefaultAggregateIdentifier(UUID.randomUUID().toString());
    }
}
