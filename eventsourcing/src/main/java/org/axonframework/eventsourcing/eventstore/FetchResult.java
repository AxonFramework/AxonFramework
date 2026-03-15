/*
 * Copyright (c) 2010-2026. Axon Framework
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

import org.axonframework.common.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Holds the result of a single batch fetch from an event storage engine.
 * <p>
 * Separates the events that matched the {@link org.axonframework.messaging.eventstreaming.StreamingCondition}
 * and should be emitted to the caller ({@link #items}) from the cursor that tracks the furthest position the
 * storage engine has scanned ({@link #cursor}).
 * <p>
 * When {@code items} is non-empty, {@code cursor} equals {@code items.getLast()}.
 * When {@code items} is empty but the storage engine fetched (and discarded) non-matching events,
 * {@code cursor} is a position-only marker. When the database returned nothing at all, {@code cursor} is
 * {@code null}.
 *
 * @param <E>    the element type used by {@link ContinuousMessageStream}
 * @param items  the elements that matched the condition and should be emitted; never {@code null}
 * @param cursor the last element scanned in the batch, regardless of match; {@code null} when the DB
 *               returned no rows
 * @author Markus Eckstein
 * @since 5.0.4
 */
@Internal
public record FetchResult<E>(List<E> items, @Nullable E cursor) {

    public FetchResult {
        Objects.requireNonNull(items, "items must not be null");
        if (!items.isEmpty()) {
            E lastItem = items.getLast();
            if (!lastItem.equals(cursor)) {
                String message = """
                    When items is non-empty, cursor must equal the last item.
                    Got cursor: %s, last item: %s
                    """.formatted(cursor, lastItem);

                throw new IllegalArgumentException(message);
            }
        }
    }
}
