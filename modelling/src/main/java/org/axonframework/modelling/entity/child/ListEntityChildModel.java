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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.modelling.entity.EntityModel;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An {@link EntityChildModel} that handles commands and events for a list of child entities. It will use the provided
 * {@link ChildEntityFieldDefinition} to resolve the child entities from the parent entity. Once the entities are
 * resolved, it will delegate the command- and event-handling to the child entity model(s), based on the
 * {@code commandTargetMatcher} and {@code eventTargetMatcher} respectively.
 * <p>
 * If the result of {@link ChildEntityFieldDefinition#getChildValue(Object)} is null, an empty list will be assumed.
 *
 * @param <C> The type of the child entity.
 * @param <P> The type of the parent entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ListEntityChildModel<C, P> extends AbstractEntityChildModel<C, P> {

    private final ChildEntityFieldDefinition<P, List<C>> childEntityFieldDefinition;

    private ListEntityChildModel(
            @Nonnull EntityModel<C> childEntityModel,
            @Nonnull ChildEntityFieldDefinition<P, List<C>> childEntityFieldDefinition,
            @Nonnull ChildEntityMatcher<C, CommandMessage<?>> commandTargetMatcher,
            @Nonnull ChildEntityMatcher<C, EventMessage<?>> eventTargetMatcher
    ) {
        super(childEntityModel, commandTargetMatcher, eventTargetMatcher);
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
    public EntityModel<C> entityModel() {
        return childEntityModel;
    }

    @Override
    public String toString() {
        return "ListEntityChildModel{entityType=" + entityType().getName() + '}';
    }

    /**
     * Creates a new {@link Builder} for the given parent class and child entity model. The
     * {@link ChildEntityFieldDefinition} is required to resolve the child entities from the parent entity and evolve
     * the parent entity based on the child entities. The {@link ChildEntityMatcher commandTargetMatcher} and
     * {@link ChildEntityMatcher eventTargetMatcher} are used to match the child entities to the command and event
     * respectively. Without specifying these matchers, all child entities will be considered for all commands and
     * events.
     *
     * @param parentClass The class of the parent entity.
     * @param entityModel The {@link EntityModel} of the child entity.
     * @param <C>         The type of the child entity.
     * @param <P>         The type of the parent entity.
     * @return A new {@link Builder} for the given parent class and child entity model.
     */
    @Nonnull
    public static <C, P> Builder<C, P> forEntityModel(@Nonnull Class<P> parentClass,
                                                      @Nonnull EntityModel<C> entityModel
    ) {
        return new Builder<>(parentClass, entityModel);
    }

    /**
     * Builder for creating a {@link ListEntityChildModel} for the given parent class and child entity model. The
     * builder can be used to configure the child entity model and create a new instance of
     * {@link ListEntityChildModel}. The {@link ChildEntityFieldDefinition} is required to resolve the child entities
     * from the parent entity and evolve the parent entity based on the child entities. The
     * {@link ChildEntityMatcher commandTargetMatcher} and {@link ChildEntityMatcher eventTargetMatcher} are used to
     * match the child entities to the command and event respectively. Without specifying these matchers, all child
     * entities will be considered for all commands and events.
     *
     * @param <C> The type of the child entity.
     * @param <P> The type of the parent entity.
     */
    public static class Builder<C, P> extends AbstractEntityChildModel.Builder<C, P, Builder<C, P>> {
        private ChildEntityFieldDefinition<P, List<C>> childEntityFieldDefinition;

        @SuppressWarnings("unused") // Is used for generics
        private Builder(@Nonnull Class<P> parentClass,
                        @Nonnull EntityModel<C> childEntityModel) {
            super(parentClass, childEntityModel);
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
         * Builds a new {@link ListEntityChildModel} instance with the configured properties. The
         * {@link ChildEntityFieldDefinition} is required to be set before calling this method.
         *
         * @return A new {@link ListEntityChildModel} instance with the configured properties.
         */
        public ListEntityChildModel<C, P> build() {
            return new ListEntityChildModel<>(
                    childEntityModel,
                    childEntityFieldDefinition,
                    commandTargetMatcher,
                    eventTargetMatcher
            );
        }
    }
}
