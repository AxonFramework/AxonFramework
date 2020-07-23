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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.property.Property;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.ForwardingMode;

import java.lang.reflect.Member;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.resolveMemberGenericType;

/**
 * Implementation of a {@link AbstractChildEntityDefinition} that is used to detect Maps with entities as values
 * annotated with {@link AggregateMember}. If such a field or method is found a {@link ChildEntity} is created that
 * delegates to the entities in the annotated Map.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class AggregateMemberAnnotatedChildEntityMapDefinition extends AbstractChildEntityDefinition {

    @Override
    protected boolean isMemberTypeSupported(Member member) {
        try {
            Class<?> memberValueType = ReflectionUtils.getMemberValueType(member);
            return Map.class.isAssignableFrom(memberValueType);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    protected <T> EntityModel<Object> extractChildEntityModel(EntityModel<T> declaringEntity,
                                                              Map<String, Object> attributes,
                                                              Member member) {
        Class<?> entityType = (Class<?>) attributes.get("type");
        if (Void.class.equals(entityType)) {
            entityType = resolveMemberGenericType(member, 1).orElseThrow(
                    () -> new AxonConfigurationException(format(
                            "Unable to resolve entity type of field [%s]. Please provide type explicitly in @AggregateMember annotation.",
                            ReflectionUtils.getMemberGenericString(member)
                    )));
        }

        return declaringEntity.modelOf(entityType);
    }

    @Override
    protected <T> Object resolveCommandTarget(CommandMessage<?> msg,
                                              T parent,
                                              Member member,
                                              EntityModel<Object> childEntityModel) {
        Map<String, Property<Object>> commandHandlerRoutingKeys =
                extractCommandHandlerRoutingKeys(member, childEntityModel);

        Object routingValue = commandHandlerRoutingKeys.get(msg.getCommandName())
                                                       .getValue(msg.getPayload());
        Map<?, ?> fieldValue = ReflectionUtils.getMemberValue(member, parent);

        return fieldValue == null ? null : fieldValue.get(routingValue);
    }

    @Override
    protected <T> Stream<Object> resolveEventTargets(EventMessage message,
                                                     T parentEntity,
                                                     Member member,
                                                     ForwardingMode eventForwardingMode) {
        Map<?, Object> memberValue = ReflectionUtils.getMemberValue(member, parentEntity);
        return memberValue == null
                ? Stream.empty()
                : eventForwardingMode.filterCandidates(message, memberValue.values().stream());
    }
}
