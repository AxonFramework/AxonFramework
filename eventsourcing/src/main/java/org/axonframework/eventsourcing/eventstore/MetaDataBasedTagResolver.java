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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventstreaming.Tag;

import java.util.Objects;
import java.util.Set;

/**
 * Implementation of the {@link TagResolver} that resolves {@link Tag Tags} based on a metadata key from an
 * {@link EventMessage}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MetaDataBasedTagResolver implements TagResolver {

    private final String metaDataKey;

    /**
     * Constructs a {@link MetaDataBasedTagResolver} using the given metadata key.
     *
     * @param metaDataKey The key to extract the tag value from the event's metadata.
     */
    public MetaDataBasedTagResolver(String metaDataKey) {
        this.metaDataKey = Objects.requireNonNull(metaDataKey, "MetaDataKey cannot be null");
    }

    @Override
    public Set<Tag> resolve(@Nonnull EventMessage<?> event) {
        var tagValue = event.getMetaData().get(metaDataKey);
        return tagValue == null ? Set.of() : Set.of(new Tag(metaDataKey, tagValue));
    }
} 