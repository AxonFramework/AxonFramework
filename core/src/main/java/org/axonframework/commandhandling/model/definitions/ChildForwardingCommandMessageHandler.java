/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.commandhandling.model.definitions;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.model.inspection.CommandMessageHandler;
import org.axonframework.messaging.Message;

import java.lang.annotation.Annotation;
import java.util.function.BiFunction;

public class ChildForwardingCommandMessageHandler<T, C> implements CommandMessageHandler<T> {

    private final CommandMessageHandler<? super C> childHandler;
    private final BiFunction<CommandMessage<?>, T, C> childEntityResolver;

    public ChildForwardingCommandMessageHandler(CommandMessageHandler<? super C> childHandler,
                                                BiFunction<CommandMessage<?>, T, C> childEntityResolver) {
        this.childHandler = childHandler;
        this.childEntityResolver = childEntityResolver;
    }

    @Override
    public String commandName() {
        return childHandler.commandName();
    }

    @Override
    public boolean isFactoryHandler() {
        return childHandler.isFactoryHandler();
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

    @SuppressWarnings("unchecked")
    @Override
    public Object handle(Message<?> message, T target) {
        C childEntity = childEntityResolver.apply((CommandMessage<?>) message, target);
        if (childEntity == null) {
            throw new IllegalStateException("Aggregate cannot handle this command, as there is no entity instance to forward it to.");
        }
        return childHandler.handle(message, childEntity);
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return childHandler.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return childHandler.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return childHandler.getDeclaredAnnotations();
    }
}
