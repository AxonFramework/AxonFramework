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

package org.axonframework.messaging.core.interception.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.core.unitofwork.LegacyMessageSupportingContext;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Interface to interact with a MessageHandlingMember instance through a chain of interceptors, which were used to build
 * up this chain. Unlike regular handlers, interceptors have the ability to act on messages on their way to the regular
 * handler, and have the ability to block these messages.
 *
 * @param <T> The type that declares the handlers in this chain
 * @author Allard Buijze
 * @since 4.4.0
 */
public interface MessageHandlerInterceptorMemberChain<T> {

    /**
     * Handle the given {@code message} by passing it through the interceptors and ultimately to the given
     * {@code handler} on the given {@code target} instance. The result of this invocation is the result as given by the
     * {@code handler}, possibly modified by any of the interceptors in this chain.
     *
     * @param message The message to pass through the interceptor chain.
     * @param context The context in which the message is being handled.
     * @param target  The target instance to invoke the interceptors and handlers on.
     * @param handler The actual handler to invoke once all interceptors have received the message.
     * @return The result as returned by the handlers or interceptors.
     * @throws Exception Any exception thrown by the handler or any of the interceptors.
     */
    // TODO Remove entirely once #3065, #3195, #3517, and #3728 have been resolved.
    @Internal
    @Deprecated(forRemoval = true, since = "5.2.0")
    Object handleSync(@Nonnull Message message,
                      @Nonnull ProcessingContext context,
                      @Nonnull T target,
                      @Nonnull MessageHandlingMember<? super T> handler
    ) throws Exception;

    // TODO Remove entirely once #3065, #3195, #3517, and #3728 have been resolved.
    @Internal
    @Deprecated(forRemoval = true, since = "5.2.0")
    default Object handleSync(@Nonnull Message message,
                              @Nonnull T target,
                              @Nonnull MessageHandlingMember<? super T> handler
    ) throws Exception {
        ProcessingContext processingContext = new LegacyMessageSupportingContext(message);
        return handleSync(message, processingContext, target, handler);
    }

    default MessageStream<?> handle(@Nonnull Message message,
                                    @Nonnull ProcessingContext context,
                                    @Nonnull T target,
                                    @Nonnull MessageHandlingMember<? super T> handler) {
        try {
            Object result = handleSync(message, context, target, handler);
            return MessageStream.just(new GenericMessage(new MessageType(result.getClass()), result));
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }
}
