/*
 * Copyright (c) 2010-2016. Axon Framework
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

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class ChildForwardingCommandMessageHandler<P, C> implements CommandMessageHandler<P> {

    private final String parentRoutingKey;
    private final CommandMessageHandler<? super C> childHandler;
    private final BiFunction<CommandMessage<?>, P, C> childEntityResolver;

    public ChildForwardingCommandMessageHandler(String parentRoutingKey,
                                                CommandMessageHandler<? super C> childHandler,
                                                BiFunction<CommandMessage<?>, P, C> childEntityResolver) {
        this.parentRoutingKey = parentRoutingKey;
        this.childHandler = childHandler;
        this.childEntityResolver = childEntityResolver;
    }

    @Override
    public String commandName() {
        return childHandler.commandName();
    }

    @Override
    public String routingKey() {
        return parentRoutingKey;
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
    public Object handle(Message<?> message, P target) throws Exception {
        C childEntity = childEntityResolver.apply((CommandMessage<?>) message, target);
        if (childEntity == null) {
            throw new IllegalStateException("Aggregate cannot handle this command, as there is no entity instance to forward it to.");
        }
        return childHandler.handle(message, childEntity);
    }

    @Override
    public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
        return childHandler.unwrap(handlerType);
    }

    @Override
    public Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType) {
        return childHandler.annotationAttributes(annotationType);
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return childHandler.hasAnnotation(annotationType);
    }
}
