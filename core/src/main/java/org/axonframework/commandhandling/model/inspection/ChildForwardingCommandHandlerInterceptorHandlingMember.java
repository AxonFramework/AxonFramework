/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Implementation of {@link CommandHandlerInterceptorHandlingMember} that forwards command intercepting to child entity.
 *
 * @param <P> the parent entity type
 * @param <C> the child entity type
 * @author Milan Savic
 * @since 3.3
 */
public class ChildForwardingCommandHandlerInterceptorHandlingMember<P, C>
        implements CommandHandlerInterceptorHandlingMember<P> {

    private final MessageHandlingMember<? super C> childHandler;
    private final BiFunction<CommandMessage<?>, P, C> childEntityResolver;

    /**
     * Initializes this handling member with child handler and resolver for child entity.
     *
     * @param childHandler        command handler interceptor on child entity
     * @param childEntityResolver will resolve the target entity on which the interceptor will be invoked
     */
    public ChildForwardingCommandHandlerInterceptorHandlingMember(
            MessageHandlingMember<? super C> childHandler,
            BiFunction<CommandMessage<?>, P, C> childEntityResolver) {
        this.childHandler = childHandler;
        this.childEntityResolver = childEntityResolver;
    }

    @Override
    public boolean shouldInvokeInterceptorChain() {
        return childHandler.unwrap(CommandHandlerInterceptorHandlingMember.class)
                           .map(CommandHandlerInterceptorHandlingMember::shouldInvokeInterceptorChain)
                           .orElse(false);
    }

    @Override
    public Class<?> payloadType() {
        return childHandler.payloadType();
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean canHandle(Message<?> message) {
        return childHandler.canHandle(message);
    }

    @Override
    public Object handle(Message<?> message, P target) throws Exception {
        C childEntity = childEntityResolver.apply((CommandMessage<?>) message, target);
        if (childEntity == null) {
            throw new IllegalStateException("There is no child entity to intercept this command.");
        }
        return childHandler.handle(message, childEntity);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
        if (handlerType.isInstance(this)) {
            return (Optional<HT>) Optional.of(this);
        }
        return childHandler.unwrap(handlerType);
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return childHandler.hasAnnotation(annotationType);
    }

    @Override
    public Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType) {
        return childHandler.annotationAttributes(annotationType);
    }
}
