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

public class CommandMessageHandlerInterceptorChain
        implements MessageHandlerInterceptorChain<CommandMessage<?>> {

    private final CommandHandler handler;
    private final Iterator<MessageHandlerInterceptor<CommandMessage<?>>> chain;

    public CommandMessageHandlerInterceptorChain(CommandHandler handler,
                                                 List<MessageHandlerInterceptor<CommandMessage<?>>> handlerInterceptors) {
        this.handler = handler;
        this.chain = handlerInterceptors.iterator();
    }

    @Override
    public MessageStream<?> proceed(
            @Nonnull CommandMessage<?> message,
            @Nonnull ProcessingContext context
    ) {
        if (chain.hasNext()) {
            return chain.next().interceptOnHandle(message, context, this); // command message -> single(command message)
        } else {
            return handler.handle(message, context); // command message -> single(command response message)
        }
    }

}
