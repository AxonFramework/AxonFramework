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

import java.util.Objects;
import java.util.Set;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * An {@link EventCriteria} implementation dedicated towards a single {@link Tag}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
final class SingleTagCriteria implements EventCriteria {

    private final Tag tag;

    /**
     * Construct a {@link SingleTagCriteria} using the given {@code tag} as the singular {@link Tag} of this
     * {@link EventCriteria}.
     *
     * @param tag The singular {@link Tag} of this {@link EventCriteria}.
     */
    SingleTagCriteria(@Nonnull Tag tag) {
        assertNonNull(tag, "The Tag cannot be null");

        this.tag = tag;
    }

    @Override
    public Set<String> types() {
        return Set.of();
    }

    @Override
    public Set<Tag> tags() {
        return Set.of(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SingleTagCriteria that = (SingleTagCriteria) o;
        return Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tag);
    }
}
