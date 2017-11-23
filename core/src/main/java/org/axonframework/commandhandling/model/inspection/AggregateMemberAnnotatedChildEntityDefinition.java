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

import java.lang.reflect.Field;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

/**
 * Implementation of a {@link ChildEntityDefinition} that is used to detect single entities annotated with
 * {@link AggregateMember}. If such a field is found a {@link ChildEntity} is created that delegates to the entity.
 */
public class AggregateMemberAnnotatedChildEntityDefinition extends AbstractChildEntityDefinition {

    @Override
    protected boolean fieldIsOfType(Field field) {
        return Iterable.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType());
    }

    @Override
    protected <T> EntityModel<Object> extractChildEntityModel(EntityModel<T> declaringEntity,
                                                              Map<String, Object> attributes, Field field) {
        return declaringEntity.modelOf(field.getType());
    }

    @Override
    protected <T> Object createCommandTargetResolvers(CommandMessage<?> msg, T parent,
                                                      Field field, EntityModel<Object> childEntityModel) {
        return ReflectionUtils.getFieldValue(field, parent);
    }

    @Override
    protected <T> Iterable<Object> createEventTargetResolvers(Field field, T parent) {
        Object fieldVal = ReflectionUtils.getFieldValue(field, parent);
        return fieldVal == null ? emptyList() : singleton(fieldVal);
    }
}
