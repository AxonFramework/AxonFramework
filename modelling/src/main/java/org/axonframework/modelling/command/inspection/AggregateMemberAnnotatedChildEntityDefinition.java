/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.common.ReflectionUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.ForwardingMode;

import java.lang.reflect.Member;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Implementation of a {@link ChildEntityDefinition} that is used to detect single entities annotated with {@link
 * AggregateMember}. If such a field or method is found a {@link ChildEntity} is created that delegates to the entity.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class AggregateMemberAnnotatedChildEntityDefinition extends AbstractChildEntityDefinition {

    @Override
    protected boolean isMemberTypeSupported(Member member) {
        try {
            Class<?> valueType = ReflectionUtils.getMemberValueType(member);
            return !Iterable.class.isAssignableFrom(valueType) && !Map.class.isAssignableFrom(valueType);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    protected <T> EntityModel<Object> extractChildEntityModel(EntityModel<T> declaringEntity,
                                                              Map<String, Object> attributes,
                                                              Member member) {
        Class<?> entityClass = ReflectionUtils.getMemberValueType(member);
        return declaringEntity.modelOf(entityClass);
    }

    @Override
    protected <T> Object resolveCommandTarget(CommandMessage<?> msg,
                                              T parent,
                                              Member member,
                                              EntityModel<Object> childEntityModel) {
        return ReflectionUtils.getMemberValue(member, parent);
    }

    @Override
    protected <T> Stream<Object> resolveEventTargets(EventMessage message,
                                                     T parentEntity,
                                                     Member member,
                                                     ForwardingMode eventForwardingMode) {
        Object memberValue = ReflectionUtils.getMemberValue(member, parentEntity);
        return memberValue == null
                ? Stream.empty()
                : eventForwardingMode.filterCandidates(message, Stream.of(memberValue));
    }
}
