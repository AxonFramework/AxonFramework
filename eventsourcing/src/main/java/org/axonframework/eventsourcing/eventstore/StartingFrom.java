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
import org.axonframework.eventhandling.TrackingToken;

import javax.annotation.Nullable;

/**
 * An implementation of the {@link StreamingCondition} that will start
 * {@link StreamableEventSource#open(String, StreamingCondition) streaming} from the given {@code position}.
 *
 * @param position The {@link TrackingToken} describing the position to start streaming from.
 * @author Steven van Beelen
 * @since 5.0.0
 */
record StartingFrom(@Nullable TrackingToken position) implements StreamingCondition {

    @Override
    public StreamingCondition or(@Nonnull EventCriteria criteria) {
        if (position == null) {
            throw new IllegalArgumentException("The position may not be null when adding criteria to it");
        }
        return new DefaultStreamingCondition(position, criteria);
    }
}
