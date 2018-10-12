/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.ForwardingMode;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.property.Property;
import org.axonframework.eventhandling.EventMessage;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.resolveGenericType;

/**
 * Implementation of a {@link AbstractChildEntityDefinition} that is used to detect Maps with entities as
 * values annotated with {@link AggregateMember}. If such a field is found a {@link ChildEntity} is created that
 * delegates to the entities in the annotated Map.
 */
public class AggregateMemberAnnotatedChildEntityMapDefinition extends AbstractChildEntityDefinition {

    @Override
    protected boolean isFieldTypeSupported(Field field) {
        return Map.class.isAssignableFrom(field.getType());
    }

    @Override
    protected <T> EntityModel<Object> extractChildEntityModel(EntityModel<T> declaringEntity,
                                                              Map<String, Object> attributes,
                                                              Field field) {
        Class<?> entityType = (Class<?>) attributes.get("type");
        if (Void.class.equals(entityType)) {
            entityType = resolveGenericType(field, 1).orElseThrow(
                    () -> new AxonConfigurationException(format(
                            "Unable to resolve entity type of field [%s]. Please provide type explicitly in @AggregateMember annotation.",
                            field.toGenericString()
                    )));
        }

        return declaringEntity.modelOf(entityType);
    }

    @Override
    protected <T> Object resolveCommandTarget(CommandMessage<?> msg,
                                              T parent,
                                              Field field,
                                              EntityModel<Object> childEntityModel) {
        Map<String, Property<Object>> commandHandlerRoutingKeys =
                extractCommandHandlerRoutingKeys(field, childEntityModel);

        Object routingValue = commandHandlerRoutingKeys.get(msg.getCommandName())
                                                       .getValue(msg.getPayload());
        Map<?, ?> fieldValue = ReflectionUtils.getFieldValue(field, parent);

        return fieldValue == null ? null : fieldValue.get(routingValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> Stream<Object> resolveEventTargets(EventMessage message,
                                                     T parentEntity,
                                                     Field field,
                                                     ForwardingMode eventForwardingMode) {
        Map<?, Object> fieldValue = ReflectionUtils.getFieldValue(field, parentEntity);
        return fieldValue == null
                ? Stream.empty()
                : eventForwardingMode.filterCandidates(message, fieldValue.values().stream());
    }
}
