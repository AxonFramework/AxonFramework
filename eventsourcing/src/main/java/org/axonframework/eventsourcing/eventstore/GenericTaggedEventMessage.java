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

import java.util.Set;
import java.util.function.Function;

/**
 * Implementation of the {@link TaggedEventMessage} allowing a generic {@link EventMessage} of type {@code E}.
 *
 * @param event The {@link EventMessage} paired with the given {@code tags}.
 * @param tags  The {@link Set} of {@link Tag Tags} relating to the given {@code event}.
 * @param <E>   The type of {@link EventMessage} carried by this {@link TaggedEventMessage} implementation.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public record GenericTaggedEventMessage<E extends EventMessage<?>>(
        @Nonnull E event,
        @Nonnull Set<Tag> tags
) implements TaggedEventMessage<E> {

    /**
     * Compact constructing asserting that the given {@code event} and {@code tags} are not {@code null}.
     */
    public GenericTaggedEventMessage {
        Assert.notNull(event, () -> "The given event must not be null");
        Assert.notNull(tags, () -> "The given tags collection must not be null");
    }

    @Override
    public TaggedEventMessage<E> updateTags(@Nonnull Function<Set<Tag>, Set<Tag>> updater) {
        return new GenericTaggedEventMessage<>(this.event, updater.apply(this.tags));
    }
}
