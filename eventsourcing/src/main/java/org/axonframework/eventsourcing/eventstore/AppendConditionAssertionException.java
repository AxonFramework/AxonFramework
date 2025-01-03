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

/**
 * A {@link RuntimeException} implementation dedicated towards failed assertions on an {@link AppendCondition}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO #3092 - Validate exception usage
public class AppendConditionAssertionException extends RuntimeException {

    private AppendConditionAssertionException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code AppendConditionAssertionException} noting that the {@link AsyncEventStorageEngine} contains
     * events matching the {@link AppendCondition#criteria() criteria} passed the given {@code consistencyMarker}.
     *
     * @param consistencyMarker The pointer in the {@link AsyncEventStorageEngine} after which no events should've been
     *                          appended that match the {@link EventCriteria} of an {@link AppendCondition}.
     * @return An {@code AppendConditionAssertionException} noting that the {@link AsyncEventStorageEngine} contains
     * events matching the {@link AppendCondition#criteria() criteria} passed the given {@code consistencyMarker}.
     */
    public static AppendConditionAssertionException consistencyMarkerSurpassed(ConsistencyMarker consistencyMarker) {
        return new AppendConditionAssertionException(
                "Found events matching past consistency marker [" + consistencyMarker + "] while this is not allowed."
        );
    }
}
