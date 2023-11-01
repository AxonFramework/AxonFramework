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

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface for an event message handler that defers handling to one or more other handlers.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public interface EventHandlerInvoker {

    /**
     * Check whether or not this invoker has handlers that can handle the given {@code eventMessage} for a given {@code
     * segment}.
     *
     * @param eventMessage The message to be processed
     * @param segment      The segment for which the event handler should be invoked
     * @return {@code true} if the invoker has one or more handlers that can handle the given message, {@code false}
     * otherwise
     */
    boolean canHandle(@Nonnull EventMessage<?> eventMessage, @Nonnull Segment segment);

    /**
     * Check whether or not this invoker has handlers that can handle the given {@code payloadType}.
     *
     * @param payloadType The payloadType of the message to be processed
     * @return {@code true} if the invoker has one or more handlers that can handle the given message, {@code false}
     * otherwise
     */
    default boolean canHandleType(@Nonnull Class<?> payloadType) {
        return true;
    }

    /**
     * Handle the given {@code message} for the given {@code segment}.
     * <p>
     * Callers are recommended to invoke {@link #canHandle(EventMessage, Segment)} prior to invocation, but aren't
     * required to do so. Implementations must ensure to take the given segment into account when processing messages.
     *
     * @param message The message to handle
     * @param segment The segment for which to handle the message
     * @throws Exception when an exception occurs while handling the message
     */
    void handle(@Nonnull EventMessage<?> message, @Nonnull Segment segment) throws Exception;

    /**
     * Indicates whether the handlers managed by this invoker support a reset.
     *
     * @return {@code true} if a reset is supported, otherwise {@code false}
     */
    default boolean supportsReset() {
        return true;
    }

    /**
     * Performs any activities that are required to reset the state managed by handlers assigned to this invoker.
     */
    default void performReset() {
    }

    /**
     * Performs any activities that are required to reset the state managed by handlers assigned to this invoker.
     *
     * @param resetContext a {@code R} used to support the reset operation
     * @param <R>          the type of the provided {@code resetContext}
     */
    default <R> void performReset(@Nullable R resetContext) {
        if (Objects.isNull(resetContext)) {
            performReset();
        } else {
            throw new UnsupportedOperationException(
                    "EventHandlerInvoker#performRest(R) is not implemented for a non-null reset context."
            );
        }
    }

    /**
     * This is a way for an event processor to communicate that a segment which was being processed is released. This
     * might be needed or required to free resources, are clean up state which is related to the {@link Segment}.
     *
     * @param segment the segment which was released.
     */
    default void segmentReleased(Segment segment) {
    }
}
