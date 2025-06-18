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

package org.axonframework.modelling.entity.child;

import jakarta.annotation.Nonnull;
import org.axonframework.modelling.entity.EntityMetamodel;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An {@link EntityChildMetamodel} that handles commands and events for a list of child entities. It will use
 * the provided {@link ChildEntityFieldDefinition} to resolve the child entities from the parent entity. Once the
 * entities are resolved, it will delegate the command- and event-handling to the child entity metamodel(s), based on
 * the {@code commandTargetResolver} and {@code eventTargetMatcher} respectively.
 *
 * @param <C> The type of the child entity.
 * @param <P> The type of the parent entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ListEntityChildMetamodel<C, P> extends AbstractEntityChildMetamodel<C, P> {

    private final ChildEntityFieldDefinition<P, List<C>> childEntityFieldDefinition;

    private ListEntityChildMetamodel(
            @Nonnull EntityMetamodel<C> metamodel,
            @Nonnull ChildEntityFieldDefinition<P, List<C>> childEntityFieldDefinition,
            @Nonnull CommandTargetResolver<C> commandTargetResolver,
            @Nonnull EventTargetMatcher<C> eventTargetMatcher
    ) {
        super(metamodel, commandTargetResolver, eventTargetMatcher);
        this.childEntityFieldDefinition =
                requireNonNull(childEntityFieldDefinition, "The childEntityFieldDefinition may not be null.");
    }

    @Override
    protected List<C> getChildEntities(P entity) {
        List<C> childEntities = childEntityFieldDefinition
                .getChildValue(entity);
        if (childEntities == null) {
            return List.of();
        }
        return childEntities;
    }

    @Override
    protected P applyEvolvedChildEntities(P entity, List<C> evolvedChildEntities) {
        return childEntityFieldDefinition.evolveParentBasedOnChildInput(entity, evolvedChildEntities);
    }

    @Override
    @Nonnull
    public EntityMetamodel<C> entityMetamodel() {
        return metamodel;
    }

    @Override
    public String toString() {
        return "ListEntityChildModel{entityType=" + entityType().getName() + '}';
    }

    /**
     * Creates a new {@link Builder} for the given parent class and child entity metamodel. The
     * {@link ChildEntityFieldDefinition} is required to resolve the child entities from the parent entity and evolve
     * the parent entity based on the child entities. The {@link CommandTargetResolver commandTargetResolver} and
     * {@link EventTargetMatcher eventTargetMatcher} are both required, as they are used to match the child entities to
     * the command and event respectively.
     *
     * @param parentClass              The class of the parent entity.
     * @param entityMetamodel The {@link EntityMetamodel} of the child entity.
     * @param <C>                      The type of the child entity.
     * @param <P>                      The type of the parent entity.
     * @return A new {@link Builder} for the given parent class and child entity metamodel.
     */
    @Nonnull
    public static <C, P> Builder<C, P> forEntityModel(@Nonnull Class<P> parentClass,
                                                      @Nonnull EntityMetamodel<C> entityMetamodel
    ) {
        return new Builder<>(parentClass, entityMetamodel);
    }

    /**
     * Builder for creating a {@link ListEntityChildMetamodel} for the given parent class and child entity
     * metamodel. The builder can be used to configure the child entity metamodel and create a new instance of
     * {@link ListEntityChildMetamodel}. The {@link ChildEntityFieldDefinition} is required to resolve the
     * child entities from the parent entity and evolve the parent entity based on the child entities. The
     * {@link CommandTargetResolver commandTargetResolver} and {@link EventTargetMatcher eventTargetMatcher} are both
     * required, as they are used to match the child entities to the command and event respectively.
     *
     * @param <C> The type of the child entity.
     * @param <P> The type of the parent entity.
     */
    public static class Builder<C, P> extends AbstractEntityChildMetamodel.Builder<C, P, Builder<C, P>> {

        private ChildEntityFieldDefinition<P, List<C>> childEntityFieldDefinition;

        @SuppressWarnings("unused") // Is used for generics
        private Builder(@Nonnull Class<P> parentClass, @Nonnull EntityMetamodel<C> metamodel) {
            super(parentClass, metamodel);
        }

        /**
         * Sets the {@link ChildEntityFieldDefinition} to use for resolving the child entities from the parent entity
         * and evolving the parent entity based on the evolved child entities.
         *
         * @param fieldDefinition The {@link ChildEntityFieldDefinition} to use for resolving the child entities from
         *                        the parent entity
         * @return This builder instance for a fluent API.
         */
        public Builder<C, P> childEntityFieldDefinition(
                @Nonnull ChildEntityFieldDefinition<P, List<C>> fieldDefinition) {
            this.childEntityFieldDefinition = requireNonNull(fieldDefinition,
                                                             "The childEntityFieldDefinition may not be null.");
            return this;
        }

        /**
         * Builds a new {@link ListEntityChildMetamodel} instance with the configured properties. The
         * {@link ChildEntityFieldDefinition}, {@link EventTargetMatcher}, and {@link CommandTargetResolver} are
         * required to be set before calling this method.
         *
         * @return A new {@link ListEntityChildMetamodel} instance with the configured properties.
         */
        public ListEntityChildMetamodel<C, P> build() {
            this.validate();
            return new ListEntityChildMetamodel<>(
                    metamodel,
                    childEntityFieldDefinition,
                    commandTargetResolver,
                    eventTargetMatcher
            );
        }
    }
}
