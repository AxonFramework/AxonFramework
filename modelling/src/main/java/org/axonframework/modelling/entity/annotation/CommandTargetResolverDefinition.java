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
import org.axonframework.modelling.entity.child.CommandTargetResolver;

import java.lang.reflect.Member;

/**
 * Defines how a {@link CommandTargetResolver} should be constructed for an {@link EntityMember}-annotated member of an
 * {@link AnnotatedEntityMetamodel}.
 *
 * @author Mitchell Herrijgers
 * @see AnnotatedEntityMetamodel
 * @see CommandTargetResolver
 * @see EntityMember
 * @since 5.0.0
 */
@FunctionalInterface
public interface CommandTargetResolverDefinition {

    /**
     * Creates a {@link CommandTargetResolver} for the given {@code entity} and {@code member}.
     *
     * @param metamodel The {@link AnnotatedEntityMetamodel} of the child entity.
     * @param member    The member that represents the child entity in the parent entity metamodel. This member is
     *                  typically a field or a method that returns the child entity, annotated with
     *                  {@link EntityMember}.
     * @param <E>       The type of the child entity.
     * @return A {@link CommandTargetResolver} that can be used to match child entities against messages.
     */
    @Nonnull
    <E> CommandTargetResolver<E> createCommandTargetResolver(
            @Nonnull AnnotatedEntityMetamodel<E> metamodel,
            @Nonnull Member member
    );
}
