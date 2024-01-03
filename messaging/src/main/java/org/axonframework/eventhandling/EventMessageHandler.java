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

import org.axonframework.messaging.MessageHandler;

import java.util.Objects;

/**
 * Interface to be implemented by classes that can handle events.
 *
 * @author Allard Buijze
 * @see EventBus
 * @see DomainEventMessage
 * @see EventHandler
 * @since 0.1
 */
public interface EventMessageHandler extends MessageHandler<EventMessage<?>> {

    /**
     * Process the given event. The implementation may decide to process or skip the given event. It is highly
     * unrecommended to throw any exception during the event handling process.
     *
     * @param event the event to handle
     * @return the result of the event handler invocation. Is generally ignored
     * @throws Exception when an exception is raised during event handling
     */
    Object handleSync(EventMessage<?> event) throws Exception;

    /**
     * Performs any activities that are required to reset the state managed by handlers assigned to this handler.
     */
    default void prepareReset() {
    }

    /**
     * Performs any activities that are required to reset the state managed by handlers assigned to this handler.
     *
     * @param resetContext a {@code R} used to support the reset operation
     * @param <R>          the type of the provided {@code resetContext}
     */
    default <R> void prepareReset(R resetContext) {
        if (Objects.isNull(resetContext)) {
            prepareReset();
        } else {
            throw new UnsupportedOperationException(
                    "EventMessageHandler#prepareReset(R) is not implemented for a non-null reset context."
            );
        }
    }

    /**
     * Indicates whether the handlers managed by this invoker support a reset.
     *
     * @return {@code true} if a reset is supported, otherwise {@code false}
     */
    default boolean supportsReset() {
        return true;
    }
}
