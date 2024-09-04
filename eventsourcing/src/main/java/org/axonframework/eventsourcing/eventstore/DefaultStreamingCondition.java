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

import org.axonframework.eventhandling.TrackingToken;

/**
 * Default implementation of the {@link StreamingCondition}.
 *
 * @param position The {@link TrackingToken} defining the {@link #position()} of this condition.
 * @param criteria The {@link EventCriteria} defining the {@link #criteria()} of this condition.
 * @author Steven van Beelen
 * @since 5.0.0
 */
record DefaultStreamingCondition(TrackingToken position,
                                 EventCriteria criteria) implements StreamingCondition {

    @Override
    public StreamingCondition with(EventCriteria criteria) {
        return new DefaultStreamingCondition(this.position, this.criteria.combine(criteria));
    }
}
