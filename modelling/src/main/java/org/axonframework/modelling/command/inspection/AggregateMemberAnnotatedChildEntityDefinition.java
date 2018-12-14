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
import org.axonframework.common.ReflectionUtils;
import org.axonframework.eventhandling.EventMessage;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Implementation of a {@link ChildEntityDefinition} that is used to detect single entities annotated with
 * {@link AggregateMember}. If such a field is found a {@link ChildEntity} is created that delegates to the entity.
 */
public class AggregateMemberAnnotatedChildEntityDefinition extends AbstractChildEntityDefinition {

    @Override
    protected boolean isFieldTypeSupported(Field field) {
        return !Iterable.class.isAssignableFrom(field.getType()) && !Map.class.isAssignableFrom(field.getType());
    }

    @Override
    protected <T> EntityModel<Object> extractChildEntityModel(EntityModel<T> declaringEntity,
                                                              Map<String, Object> attributes,
                                                              Field field) {
        return declaringEntity.modelOf(field.getType());
    }

    @Override
    protected <T> Object resolveCommandTarget(CommandMessage<?> msg,
                                              T parent,
                                              Field field,
                                              EntityModel<Object> childEntityModel) {
        return ReflectionUtils.getFieldValue(field, parent);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> Stream<Object> resolveEventTargets(EventMessage message,
                                                     T parentEntity,
                                                     Field field,
                                                     ForwardingMode eventForwardingMode) {
        Object fieldVal = ReflectionUtils.getFieldValue(field, parentEntity);
        return fieldVal == null ? Stream.empty() : eventForwardingMode.filterCandidates(message, Stream.of(fieldVal));
    }
}
