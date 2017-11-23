/*
 * Copyright (c) 2010-2017. Axon Framework
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
import org.axonframework.commandhandling.model.AggregateMember;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.property.Property;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Implementation of a {@link AbstractChildEntityDefinition} that is used to detect Collections of entities
 * (field type assignable to {@link Iterable}) annotated with {@link AggregateMember}. If such a field is found a {@link
 * ChildEntity} is created that delegates to the entities in the annotated collection.
 */
public class AggregateMemberAnnotatedChildEntityCollectionDefinition extends AbstractChildEntityDefinition {

    @Override
    protected Optional<Class<?>> resolveGenericType(Field field) {
        return ReflectionUtils.resolveGenericType(field, 0);
    }

    @Override
    protected boolean fieldIsOfType(Field field) {
        return !Iterable.class.isAssignableFrom(field.getType());
    }

    @Override
    protected <T> Object createCommandTargetResolvers(CommandMessage<?> msg,
                                                      T parent,
                                                      Map<String, Property<Object>> commandHandlerRoutingKeys,
                                                      Field field,
                                                      EntityModel<Object> childEntityModel) {
        Object routingValue = commandHandlerRoutingKeys.get(msg.getCommandName())
                                                       .getValue(msg.getPayload());
        Iterable<?> iterable = ReflectionUtils.getFieldValue(field, parent);
        return StreamSupport.stream(iterable.spliterator(), false)
                            .filter(i -> Objects.equals(routingValue, childEntityModel.getIdentifier(i)))
                            .findFirst()
                            .orElse(null);
    }

    @Override
    protected <T> Iterable<Object> createEventTargetResolvers(Field field, T parent) {
        return ReflectionUtils.getFieldValue(field, parent);
    }
}
