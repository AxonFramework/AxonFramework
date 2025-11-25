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
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.Tag;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code TagResolver} that combines the results of multiple other tag resolvers. When multiple resolvers provide the
 * same tags, they are merged into a single set.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MultiTagResolver implements TagResolver {

    private final List<? extends TagResolver> delegates;

    /**
     * Initialize the tag resolver, delegating to given {@code tagResolvers}.
     *
     * @param tagResolvers The resolvers to delegate to.
     */
    public MultiTagResolver(@Nonnull List<? extends TagResolver> tagResolvers) {
        this.delegates = List.copyOf(tagResolvers);
    }

    /**
     * Initialize the tag resolver, delegating to given {@code tagResolvers}.
     *
     * @param tagResolvers The resolvers to delegate to.
     */
    public MultiTagResolver(@Nonnull TagResolver... tagResolvers) {
        this.delegates = List.of(tagResolvers);
    }

    @Nonnull
    @Override
    public Set<Tag> resolve(@Nonnull EventMessage event) {
        Set<Tag> tags = new HashSet<>();
        for (TagResolver delegate : delegates) {
            tags.addAll(delegate.resolve(event));
        }
        return tags;
    }
}