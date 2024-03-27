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

package org.axonframework.commandhandling.tracing;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.tracing.Span;

/**
 * Span factory that creates spans for the {@link CommandBus}. You can customize the spans of the bus by creating your
 * own implementation.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public interface CommandBusSpanFactory {

    /**
     * Creates a span for the dispatching of a command.
     *
     * @param commandMessage The command message to create a span for.
     * @param distributed    Whether the command is distributed or not.
     * @return The created span.
     */
    Span createDispatchCommandSpan(CommandMessage<?> commandMessage, boolean distributed);

    /**
     * Creates a span for the handling of a command.
     *
     * @param commandMessage The command message to create a span for.
     * @param distributed    Whether the command is distributed or not.
     * @return The created span.
     */
    Span createHandleCommandSpan(CommandMessage<?> commandMessage, boolean distributed);

    /**
     * Propagates the context of the current span to the given command message.
     *
     * @param commandMessage The command message to propagate the context to.
     * @param <T>            The type of the payload of the command message.
     * @return The command message with the propagated context.
     */
    <T> CommandMessage<T> propagateContext(CommandMessage<T> commandMessage);
}
