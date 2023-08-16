/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling;

/**
 * Type that can be used as parameter of Event Handler methods that indicates whether a message is delivered as part of
 * a replay, or in regular operations. Messages delivered as part of a replay may have been handled by this handler
 * before.
 * <p>
 * Note that this is only sensible for event handlers that are assigned to a {@link StreamingEventProcessor}. Event
 * Handlers assigned to a different mechanism, such as the {@link SubscribingEventProcessor} or certain extensions, will
 * always receive {@code ReplayStatus.REGULAR} as value.
 *
 * @author Allard Buijze
 * @see AllowReplay @AllowReplay
 * @since 3.2
 */
public enum ReplayStatus {

    /**
     * Indicates the message is delivered as part of a replay (and may have been delivered before)
     */
    REPLAY(true),
    /**
     * Indicates the message is not delivered as part of a replay (and has not been delivered before).
     */
    REGULAR(false);

    private final boolean isReplay;

    ReplayStatus(boolean isReplay) {
        this.isReplay = isReplay;
    }

    /**
     * Indicates whether this status represents a replay.
     *
     * @return {@code true} if this status indicates a replay, otherwise {@code false}
     */
    public boolean isReplay() {
        return isReplay;
    }
}
