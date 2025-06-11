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
import org.axonframework.common.annotation.Internal;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.child.CommandTargetResolver;
import org.axonframework.modelling.entity.child.EntityChildModel;
import org.axonframework.modelling.entity.child.EventTargetMatcher;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.axonframework.common.ReflectionUtils.fieldNameFromMember;
import static org.axonframework.common.ReflectionUtils.getMemberValueType;
import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

/**
 * Abstract implementation of the {@link EntityChildModelDefinition} interface that makes concrete implementations
 * easier to maintain. It constructs the necessary definitions from the {@link EntityMember} annotation, determines the
 * field name based on the member and calls the
 * {@link #doCreate(Class, EntityModel, String, EventTargetMatcher, CommandTargetResolver)} method to create the actual
 * child model.
 * <p>
 * Implementors define what kind of fields they support by implementing the {@link #isMemberTypeSupported(Class)}
 * method. If this method returns {@code true}, the {@link #getChildTypeFromMember(Member)} will be called to determine
 * the child type (which may be a generic argument, such as when using a {@link List} as a field type). Then, the
 * {@link #doCreate(Class, EntityModel, String, EventTargetMatcher, CommandTargetResolver)} methods will be called with
 * all information needed to create the child model.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public abstract class AbstractEntityChildModelDefinition implements EntityChildModelDefinition {

    @Nonnull
    @Override
    public <C, P> Optional<EntityChildModel<C, P>> createChildDefinition(
            @Nonnull Class<P> parentClass,
            @Nonnull AnnotatedEntityModelFactory entityModelFactory,
            @Nonnull Member member
    ) {
        Map<String, Object> attributes = findAnnotationAttributes((AnnotatedElement) member, EntityMember.class)
                .orElse(null);
        Class<?> memberValueType = getMemberValueType(member);
        if (attributes == null || !isMemberTypeSupported(memberValueType)) {
            return Optional.empty();
        }

        //noinspection unchecked - this is the actual C type
        Class<C> childType = (Class<C>) getChildTypeFromMember(member);
        AnnotatedEntityModel<C> childModel = entityModelFactory.createModelForType(childType);

        String fieldName = fieldNameFromMember(member);
        var eventForwardingMode = constructForwardingDefinition(attributes)
                .createChildEntityMatcher(childModel, member);
        var commandForwardingMode = constructCommandChildEntityResolver(attributes)
                .createCommandTargetResolver(childModel, member);

        return Optional.of(doCreate(parentClass, childModel, fieldName, eventForwardingMode, commandForwardingMode));
    }

    /**
     * Check if the given member type supports this definition. Returning {@code true} from this method implies that the
     * {@link #getChildTypeFromMember(Member)} and
     * {@link #doCreate(Class, EntityModel, String, EventTargetMatcher, CommandTargetResolver)} methods will be called.
     *
     * @param memberType The type of the member to check.
     * @return Should return {@code true} if the member type is supported, {@code false} otherwise.
     */
    protected abstract boolean isMemberTypeSupported(Class<?> memberType);

    /**
     * Returns the actual child type. If it needs to be retrieved from a generic, this method should do so.
     * This is used to construct the child {@link EntityModel} using the {@link AnnotatedEntityModelFactory} supplied
     * by the parent entity model.
     *
     * @param member The member to retrieve the child type from.
     * @return The child type.
     */
    protected abstract Class<?> getChildTypeFromMember(Member member);

    /**
     * Creates a new {@link EntityChildModel} for the given parent class and child model. This method will be called if
     * the {@link #isMemberTypeSupported(Class)} returns {@code true} for the given member type.
     *
     * @param parentClass           The class of the parent entity.
     * @param childModel            The child model to use for the child entity.
     * @param fieldName             The name of the field to use for the child entity. If the member is a field, this
     *                              will be the field name. If it is a method, the supposed field name will be the
     *                              method name without the "get", "set" or "is" prefix and starting with a lowercase
     *                              character.
     * @param eventTargetMatcher    The {@link EventTargetMatcher} to use for the child entity.
     * @param commandTargetResolver The {@link CommandTargetResolver} to use for the child entity.
     * @param <C>                   The type of the child entity.
     * @param <P>                   The type of the parent entity.
     * @return A new {@link EntityChildModel} for the given parent class and child model.
     */
    @Nonnull
    protected abstract <C, P> EntityChildModel<C, P> doCreate(
            @Nonnull Class<P> parentClass,
            @Nonnull EntityModel<C> childModel,
            @Nonnull String fieldName,
            @Nonnull EventTargetMatcher<C> eventTargetMatcher,
            @Nonnull CommandTargetResolver<C> commandTargetResolver);

    private EventTargetMatcherDefinition constructForwardingDefinition(Map<String, Object> attributes) {
        //noinspection unchecked
        Class<EventTargetMatcherDefinition> definitionClazz =
                (Class<EventTargetMatcherDefinition>) attributes.get("eventTargetMatcher");
        return ConstructorUtils.getConstructorFunctionWithZeroArguments(definitionClazz).get();
    }

    private CommandTargetResolverDefinition constructCommandChildEntityResolver(
            Map<String, Object> attributes) {
        //noinspection unchecked
        Class<CommandTargetResolverDefinition> definitionClazz =
                (Class<CommandTargetResolverDefinition>) attributes.get("commandTargetResolver");
        return ConstructorUtils.getConstructorFunctionWithZeroArguments(definitionClazz).get();
    }
}
