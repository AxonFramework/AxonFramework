/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.ConstructorUtils;
import org.axonframework.messaging.Message;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.child.ChildEntityMatcher;
import org.axonframework.modelling.entity.child.EntityChildModel;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.axonframework.common.ReflectionUtils.fieldNameFromMember;
import static org.axonframework.common.ReflectionUtils.getMemberValueType;
import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

/**
 * Abstract implementation of the {@link EntityChildModelDefinition} interface. It will construct the necessary objects
 * from the annotation, determine the field name based on the member and call the
 * {@link #doCreate(Class, EntityModel, String, ChildEntityMatcher, ChildEntityMatcher)} method to create the actual
 * child model.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public abstract class AbstractEntityChildModelDefinition implements EntityChildModelDefinition {

    @Nonnull
    @Override
    public <C, P> Optional<EntityChildModel<C, P>> createChildDefinition(@Nonnull Class<P> parentClass,
                                                                         @Nonnull Function<Class<C>, AnnotatedEntityModel<C>> entityModelFactory,
                                                                         @Nonnull Member member) {
        Map<String, Object> attributes = findAnnotationAttributes((AnnotatedElement) member, EntityMember.class)
                .orElse(null);
        Class<?> memberValueType = getMemberValueType(member);
        if (attributes == null || !isMemberTypeSupported(memberValueType)) {
            return Optional.empty();
        }

        //noinspection unchecked
        Class<C> childType = (Class<C>) getChildTypeFromMember(member);
        AnnotatedEntityModel<C> childModel = entityModelFactory.apply(childType);

        String fieldName = fieldNameFromMember(member);
        var eventForwardingMode = constructForwardingDefinition(attributes, "eventForwardingMode")
                .createChildEntityMatcher(childModel, member);
        var commandForwardingMode = constructForwardingDefinition(attributes, "commandForwardingMode")
                .createChildEntityMatcher(childModel, member);

        return Optional.of(doCreate(parentClass, childModel, fieldName, eventForwardingMode, commandForwardingMode));
    }

    /**
     * Check if the given member type is supported by this definition. Returning {@code true} from this method implies
     * that the {@link #getChildTypeFromMember(Member)} and
     * {@link #doCreate(Class, EntityModel, String, ChildEntityMatcher, ChildEntityMatcher)} methods will be called.
     *
     * @param memberType The type of the member to check.
     * @return Should return {@code true} if the member type is supported, {@code false} otherwise.
     */
    protected abstract boolean isMemberTypeSupported(Class<?> memberType);

    /**
     * Returns the actual child type. If it needs to be retrieved from a generic, this method should do so.
     *
     * @param member The member to retrieve the child type from.
     * @return The child type.
     */
    protected abstract Class<?> getChildTypeFromMember(Member member);

    /**
     * Creates a new {@link EntityChildModel} for the given parent class and child model. This method will be called if
     * the {@link #isMemberTypeSupported(Class)} returns {@code true} for the given member type.
     *
     * @param parentClass               The class of the parent entity.
     * @param childModel                The child model to use for the child entity.
     * @param fieldName                 The name of the field to use for the child entity.
     * @param eventChildEntityMatcher   The event child entity matcher to use for the child entity.
     * @param commandChildEntityMatcher The command child entity matcher to use for the child entity.
     * @param <C>                       The type of the child entity.
     * @param <P>                       The type of the parent entity.
     * @return A new {@link EntityChildModel} for the given parent class and child model.
     */
    @Nonnull
    protected abstract <C, P> EntityChildModel<C, P> doCreate(
            @Nonnull Class<P> parentClass,
            @Nonnull EntityModel<C> childModel,
            @Nonnull String fieldName,
            @Nonnull ChildEntityMatcher<C, Message<?>> eventChildEntityMatcher,
            @Nonnull ChildEntityMatcher<C, Message<?>> commandChildEntityMatcher);

    private ChildEntityMatcherDefinition constructForwardingDefinition(Map<String, Object> attributes,
                                                                       String propertyName) {
        //noinspection unchecked
        Class<ChildEntityMatcherDefinition> definitionClazz = (Class<ChildEntityMatcherDefinition>) attributes.get(
                propertyName);
        return ConstructorUtils.getConstructorFunctionWithZeroArguments(definitionClazz).get();
    }
}
