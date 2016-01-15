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
import org.axonframework.common.ReflectionUtils;
import org.axonframework.eventhandling.EventMessage;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class AnnotatedChildEntity<P, C> implements ChildEntity<P> {
    private final Field field;
    private final EntityModel<C> entityModel;
    private final boolean forwardEvents;
    private final Map<String, CommandMessageHandler<? super P>> commandHandlers;

    public AnnotatedChildEntity(String parentRoutingKey, Field field, EntityModel<C> entityModel,
                                boolean forwardCommands, boolean forwardEvents,
                                BiFunction<CommandMessage<?>, P, C> targetResolver) {
        this.field = field;
        this.entityModel = entityModel;
        this.forwardEvents = forwardEvents;

        this.commandHandlers = new HashMap<>();
        if (forwardCommands) {
            entityModel.commandHandlers()
                    .forEach((commandType, childHandler) -> {
                        commandHandlers.put(commandType, new ChildForwardingCommandMessageHandler<>(parentRoutingKey, childHandler, targetResolver));
                    });
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void publish(EventMessage<?> msg, P declaringInstance) {
        if (forwardEvents) {
            Object instance = ReflectionUtils.getFieldValue(field, declaringInstance);
            if (instance != null) {
                EntityModel runtimeModel = this.entityModel.modelOf(instance.getClass());
                runtimeModel.publish(msg, instance);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, CommandMessageHandler<? super P>> commandHandlers() {
        return commandHandlers;
    }

}
