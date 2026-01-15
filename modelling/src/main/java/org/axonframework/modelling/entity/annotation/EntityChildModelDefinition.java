/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.modelling.entity.child.EntityChildMetamodel;

import java.lang.reflect.Member;
import java.util.Optional;

/**
 * Interface describing the definition of an {@link EntityChildMetamodel}. These definitions are automatically
 * detected by the {@link AnnotatedEntityMetamodel} if the definition's implementation is registered in the
 * {@code META-INF/services/org.axonframework.modelling.entity.annotation.EntityChildModelDefinition} file.
 * <p>
 * Note: This class was known as {code org.axonframework.modelling.command.inspection.ChildEntityDefinition} before
 * version 5.0.0.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @see java.util.ServiceLoader
 * @since 3.0.0
 */
public interface EntityChildModelDefinition {

    /**
     * Inspect the given {@code member}, which is declared on the given {@code parentClass} for the presence of a child
     * entity according to this definition. If a child entity is found, an {@link EntityChildMetamodel} is
     * returned. This metamodel can use the given {@code metamodelFactory} to create the child
     * {@link EntityMetamodel} based on the class.
     *
     * @param parentClass      The class of the parent entity.
     * @param metamodelFactory A factory to create the child {@link EntityMetamodel} based on the class.
     * @param member           The member to inspect for a child entity.
     * @param <C>              The type of the child entity.
     * @param <P>              The type of the parent entity.
     * @return An {@link Optional} that resolves to an {@link EntityChildMetamodel} if the field represents a
     * child entity, or an empty optional if no child entity is found.
     */
    @Nonnull
    <C, P> Optional<EntityChildMetamodel<C, P>> createChildDefinition(
            @Nonnull Class<P> parentClass,
            @Nonnull AnnotatedEntityMetamodelFactory metamodelFactory,
            @Nonnull Member member
    );
}
