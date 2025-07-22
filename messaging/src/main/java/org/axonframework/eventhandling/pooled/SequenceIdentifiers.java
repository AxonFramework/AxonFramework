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

package org.axonframework.eventhandling.pooled;

import org.axonframework.messaging.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SequenceIdentifiers {

    /**
     * The {@link Context.ResourceKey} used whenever a {@link Context} would contain a {@link SequenceIdentifiers}.
     */
    public static final Context.ResourceKey<SequenceIdentifiers> RESOURCE_KEY = Context.ResourceKey.withLabel("sequenceIdentifiers");

    private final Map<String, Set<Object>> sequenceIdentifiersByMessage;

    public SequenceIdentifiers() {
        this.sequenceIdentifiersByMessage = new HashMap<>();
    }

    private SequenceIdentifiers(Map<String, Set<Object>> sequenceIdentifiersByMessage) {
        this.sequenceIdentifiersByMessage = sequenceIdentifiersByMessage;
    }

    public Set<Object> getSequenceIdentifiers(String messageIdentifier) {
        return sequenceIdentifiersByMessage.getOrDefault(messageIdentifier, Set.of());
    }

    public SequenceIdentifiers withSequenceIdentifiers(String messageIdentifier, Set<Object> sequenceIdentifiers) {
        Map<String, Set<Object>> newMap = new HashMap<>(sequenceIdentifiersByMessage);
        newMap.put(messageIdentifier, sequenceIdentifiers);
        return new SequenceIdentifiers(newMap);
    }

    public SequenceIdentifiers withSequenceIdentifier(String messageIdentifier, Object identifier) {
        Map<String, Set<Object>> newMap = new HashMap<>(sequenceIdentifiersByMessage);
        Set<Object> identifiers = newMap.getOrDefault(messageIdentifier, new java.util.HashSet<>());
        identifiers.add(identifier);
        newMap.put(messageIdentifier, identifiers);
        return new SequenceIdentifiers(newMap);
    }

    /**
     * Adds the given {@code sequenceIdentifiers} to the given {@code context} using the {@link #RESOURCE_KEY}.
     */
    public static Context addToContext(Context context, SequenceIdentifiers sequenceIdentifiers) {
        return context.withResource(RESOURCE_KEY, sequenceIdentifiers);
    }

    /**
     * Returns an {@link Optional} of {@link SequenceIdentifiers}, returning the resource keyed under the
     * {@link #RESOURCE_KEY} in the given {@code context}.
     */
    public static Optional<SequenceIdentifiers> fromContext(Context context) {
        return Optional.ofNullable(context.getResource(RESOURCE_KEY));
    }
}
