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

package org.axonframework.messaging.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Iterator;

/**
 * A {@link MessageHandlerInterceptorMemberChain} implementation that constructs a chain of instances of itself based on
 * a given {@code iterator} of {@link MessageHandlingMember MessageHandlingMembers}.
 *
 * @param <T> The type that declares the handlers in this chain.
 * @author Allard Buijze
 * @since 4.4.0
 */
public class ChainedMessageHandlerInterceptorMember<T> implements MessageHandlerInterceptorMemberChain<T> {

    private final MessageHandlingMember<? super T> delegate;
    private final MessageHandlerInterceptorMemberChain<T> next;

    /**
     * Constructs a chained message handling interceptor for the given {@code handlerType}, constructing a chain from
     * the given {@code iterator}.
     * <p>
     * The {@code iterator} should <em>at least</em>> have a single {@link MessageHandlingMember}. If there are more
     * {@code MessageHandlingMembers} present in the given {@code iterator}, this constructor will be invoked again.
     *
     * @param handlerType The type for which to construct a message handler interceptor chain.
     * @param iterator    The {@code MessageHandlingMembers} from which to construct the chain.
     */
    public ChainedMessageHandlerInterceptorMember(Class<?> handlerType,
                                                  Iterator<MessageHandlingMember<? super T>> iterator) {
        this.delegate = iterator.next();
        this.next = iterator.hasNext()
                ? new ChainedMessageHandlerInterceptorMember<>(handlerType, iterator)
                : NoMoreInterceptors.instance();
    }

    @Override
    public MessageStream<?> handle(@Nonnull Message<?> message,
                                   @Nonnull ProcessingContext processingContext,
                                   @Nonnull T target,
                                   @Nonnull MessageHandlingMember<? super T> handler) {
        return InterceptorChainParameterResolverFactory.callWithInterceptorChain(
                processingContext,
                () -> next.handle(message, processingContext, target, handler),
                (pc) -> doHandle(message, pc, target, handler)
        );
    }

    private MessageStream<?> doHandle(Message<?> message,
                                      ProcessingContext processingContext,
                                      T target,
                                      MessageHandlingMember<? super T> handler) {
        return delegate.canHandle(message, processingContext)
                ? delegate.handle(message, processingContext, target)
                : next.handle(message, processingContext, target, handler);
    }

    @Override
    public Object handleSync(@Nonnull Message<?> message,
                             @Nonnull T target,
                             @Nonnull MessageHandlingMember<? super T> handler) throws Exception {
        return InterceptorChainParameterResolverFactory.callWithInterceptorChainSync(
                () -> next.handleSync(message, target, handler),
                () -> doHandleSync(message, target, handler)
        );
    }

    private Object doHandleSync(Message<?> message, T target, MessageHandlingMember<? super T> handler)
            throws Exception {
        return delegate.canHandle(message, null)
                ? delegate.handleSync(message, target)
                : next.handleSync(message, target, handler);
    }
}
