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

import org.axonframework.commandhandling.model.AggregateMember;
import org.axonframework.commandhandling.model.inspection.ChildEntity;
import org.axonframework.commandhandling.model.inspection.ChildEntityDefinition;
import org.axonframework.commandhandling.model.inspection.CommandMessageHandler;
import org.axonframework.commandhandling.model.inspection.EntityModel;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.eventhandling.EventMessage;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AggregateMemberAnnotatedChildEntityDefinition implements ChildEntityDefinition {

    @Override
    public <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity) {
        AggregateMember annotation = ReflectionUtils.findAnnotation(field, AggregateMember.class);
        if (annotation == null) {
            return Optional.empty();
        }
        EntityModel<?> entityModel = declaringEntity.modelOf(field.getType());
        return Optional.of(new AnnotatedChildEntity<>(field, entityModel,
                                                      annotation.forwardCommands(), annotation.forwardEvents()));
    }

    protected boolean isCollection(Class<?> type) {
        return !Collection.class.isAssignableFrom(type) && !type.isArray() && Map.class.isAssignableFrom(type);
    }

    private static class AnnotatedChildEntity<T, C> implements ChildEntity<T> {
        private final Field field;
        private final EntityModel<C> entityModel;
        private final boolean forwardEvents;
        private final Map<String, CommandMessageHandler<? super T>> commandHandlers;

        public AnnotatedChildEntity(Field field, EntityModel<C> entityModel,
                                    boolean forwardCommands, boolean forwardEvents) {
            this.field = field;
            this.entityModel = entityModel;
            this.forwardEvents = forwardEvents;

            this.commandHandlers = new HashMap<>();
            if (forwardCommands) {
                entityModel.commandHandlers()
                        .forEach((commandType, childHandler) ->
                                         commandHandlers.put(commandType,
                                                             new ChildForwardingCommandMessageHandler<>(childHandler, (msg, parent) -> (C) ReflectionUtils.getFieldValue(field, parent))));
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void publish(EventMessage<?> msg, T declaringInstance) {
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
        public Map<String, CommandMessageHandler<? super T>> commandHandlers() {
            return commandHandlers;
        }

    }

}
