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

package org.axonframework.messaging.eventhandling.replay;

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandler;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;

/**
 * Functional interface for handling {@link ReplayStatusChange} messages.
 * <p>
 * Implementations of this interface process replay status changes, typically to prepare for and finalize an event
 * replay. Actions to consider during a {@code ReplayStatus} change are cleaning up the projection state, clearing
 * caches, or switching storage solution aliases. To that end, the {@code ReplayStatusChange} message contains the
 * {@link ReplayStatus} that will be changed to.
 * <p>
 * Replay status change handlers are registered via
 * {@link ReplayStatusChangeHandlerRegistry#subscribe(ReplayStatusChangeHandler)}.
 * <p>
 * Example usage:
 * <pre>{@code
 * ReplayStatusChangeHandler handler = (statusChange, context) -> {
 *     if (statusChange.status() == REPLAY) {
 *         repository.deleteAll();
 *         cache.clear();
 *     }
 *     return MessageStream.empty();
 * };
 * }</pre>
 *
 * @author Simon Zambrovski
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @see ReplayStatusChange
 * @see ReplayStatusChangeHandlerRegistry
 * @see org.axonframework.messaging.eventhandling.EventHandlingComponent
 * @since 5.1.0
 */
@FunctionalInterface
public interface ReplayStatusChangeHandler extends MessageHandler {

    /**
     * Handles the given {@link ReplayStatusChange} message, allowing for tasks to be performed when the
     * {@link ReplayStatus#REPLAY replay starts} and {@link ReplayStatus#REGULAR ends}.
     * <p>
     * This method is invoked on the moment the {@code ReplayStatus} is about to change. Thus, when
     * {@link StreamingEventProcessor#resetTokens() resetting} would cause a switch from regular handling to a replay.
     * Furthermore, this method will be invoked when the {@code ReplayStatus} is <b>about</b> to change from replay to
     * regular.
     * <p>
     * TODO - figure out what to do with exceptional stream results - If this method completes exceptionally, the reset operation will be aborted and no replay will occur.
     *
     * @param statusChange the replay status context message containing replay status information
     * @param context      the processing context for this operation
     * @return an empty message stream after handling completes successfully
     */
    MessageStream.Empty<Message> handle(ReplayStatusChange statusChange,
                                        ProcessingContext context);
}
