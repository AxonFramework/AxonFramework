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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;
import java.util.Set;

/**
 * An {@code Tag} refers to fields and their values within which an {@link org.axonframework.eventhandling.EventMessage}
 * has been published, typically referring to domain-specifics used for identification.
 * <p>
 * Such a {@code Tag} is typically used by the {@link EventCriteria} as a filter when
 * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) sourcing},
 * {@link StreamableEventSource#open(String, StreamingCondition) streaming} or
 * {@link EventStoreTransaction#appendEvent(EventMessage) appending} events.
 *
 * @param key   The key of this {@link Tag}.
 * @param value The value of this {@link Tag}.
 * @author Allard Buijze
 * @author Michal Negacz
 * @author Milan Savić
 * @author Marco Amann
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public record Tag(@Nonnull String key,
                  @Nonnull String value) {

    /**
     * Compact constructor validating that the given {@code key} and {@code value} are not {@code null}.
     */
    public Tag {
        Assert.notNull(key, () -> "A Tag's key cannot be null");
        Assert.notNull(value, () -> "A Tag's value cannot be null");
    }

    /**
     * The {@link ResourceKey} used whenever a {@link Context} would contain a {@link Set} of {@link Tag Tags}.
     */
    public static final ResourceKey<Set<Tag>> RESOURCE_KEY = ResourceKey.withLabel("tags");

    /**
     * Adds the given {@code token} to the given {@code context} using the {@link #RESOURCE_KEY}.
     *
     * @param context The {@link Context} to add the given {@code token} to.
     * @param tags    The {@link Set} of {@link Tag Tags} to add to the given {@code context} using the
     *                {@link #RESOURCE_KEY}.
     */
    public static Context addToContext(Context context, Set<Tag> tags) {
        return context.withResource(RESOURCE_KEY, tags);
    }

    /**
     * Returns an {@link Optional} of {@link Tag Tags}, returning the resource keyed under the {@link #RESOURCE_KEY} in
     * the given {@code context}.
     *
     * @param context The {@link Context} to retrieve the {@link Tag Tags} from, if present.
     * @return An {@link Optional} of {@link Tag Tags}, returning the resource keyed under the {@link #RESOURCE_KEY} in
     * the given {@code context}.
     */
    public static Optional<Set<Tag>> fromContext(Context context) {
        return Optional.ofNullable(context.getResource(RESOURCE_KEY));
    }
}
