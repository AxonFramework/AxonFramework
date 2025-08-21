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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Iterator;
import java.util.List;

/**
 * Command message handler interceptor chain.
 *
 * @author Simon Zambrovski
 * @since 5.0.0
 */
public class CommandMessageHandlerInterceptorChain
        implements MessageHandlerInterceptorChain<CommandMessage<?>> {

    private final Iterator<MessageHandlerInterceptor<CommandMessage<?>>> chain;
    private final CommandHandler handler;

    /**
     * Constructs a new chain with a list of interceptors and a command handler.
     *
     * @param handlerInterceptors list of interceptors.
     * @param handler             command handler.
     */
    public CommandMessageHandlerInterceptorChain(
            @Nonnull List<MessageHandlerInterceptor<CommandMessage<?>>> handlerInterceptors,
            @Nonnull CommandHandler handler) {
        this.chain = handlerInterceptors.iterator();
        this.handler = handler;
    }

    @Override
    public @Nonnull MessageStream<?> proceed(
            @Nonnull CommandMessage<?> message,
            @Nonnull ProcessingContext context
    ) {
        try {
            if (chain.hasNext()) {
                return this.chain.next()
                                 .interceptOnHandle(message, context, this)
                                 .first()
                                 .<CommandMessage<?>>cast();
            } else {
                return this.handler.handle(message, context);
            }
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }
}
