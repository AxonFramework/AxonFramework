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
 * Implementation of an AggregateIdentifier that uses a UUID as backing identifier. This class is able to generate
 * (pseudo)random identifiers (see {@link java.util.UUID#randomUUID()}) as well as reuse externally generated UUIDs as
 * identifier values.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class UUIDAggregateIdentifier extends StringAggregateIdentifier {

    private static final long serialVersionUID = 3526542215131742657L;

    /**
     * Creates a new instance using a randomly chosen UUID as identifier value.
     *
     * @see java.util.UUID#randomUUID()
     */
    public UUIDAggregateIdentifier() {
        this(UUID.randomUUID());
    }

    /**
     * Creates a new instance using the given UUID as identifier value.
     *
     * @param identifier the UUID representation of the identifier
     * @throws NullPointerException if the given identifier is <code>null</code>.
     */
    public UUIDAggregateIdentifier(UUID identifier) {
        super(identifier.toString());
    }
}
