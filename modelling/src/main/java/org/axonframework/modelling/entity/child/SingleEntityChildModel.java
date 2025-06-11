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
import org.axonframework.modelling.entity.EntityModel;

import java.util.List;
import java.util.Objects;

/**
 * An {@link EntityChildModel} that handles commands and events for a single child entity. It will use the provided
 * {@link ChildEntityFieldDefinition} to resolve the child entity from the parent entity. Once the entity is resolved,
 * it will delegate the command- and event-handling to the child entity model.
 * <p>
 * The commands and events will, by default, be forwarded unconditionally to the child entity. If you have multiple
 * member fields, and want to match commands and events to a specific child entity, you can configure the
 * {@link CommandTargetResolver} and {@link EventTargetMatcher} to match the child entity based on the command or
 * event.
 *
 * @param <C> The type of the child entity.
 * @param <P> The type of the parent entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class SingleEntityChildModel<C, P> extends AbstractEntityChildModel<C, P> {

    private final ChildEntityFieldDefinition<P, C> childEntityFieldDefinition;

    private SingleEntityChildModel(@Nonnull EntityModel<C> childEntityModel,
                                   @Nonnull ChildEntityFieldDefinition<P, C> childEntityFieldDefinition,
                                   @Nonnull CommandTargetResolver<C> commandTargetMatcher,
                                   @Nonnull EventTargetMatcher<C> eventTargetMatcher
    ) {
        super(
                childEntityModel,
                commandTargetMatcher,
                eventTargetMatcher
        );
        this.childEntityFieldDefinition = Objects.requireNonNull(
                childEntityFieldDefinition,
                "The childEntityFieldDefinition may not be null."
        );
    }

    @Override
    protected List<C> getChildEntities(P entity) {
        C childEntity = childEntityFieldDefinition
                .getChildValue(entity);
        if (childEntity != null) {
            return List.of(childEntity);
        }
        return List.of();
    }

    @Override
    protected P applyEvolvedChildEntities(P entity, List<C> evolvedChildEntities) {
        if (evolvedChildEntities.isEmpty()) {
            return childEntityFieldDefinition.evolveParentBasedOnChildInput(entity, null);
        }
        if (evolvedChildEntities.size() > 1) {
            throw new IllegalStateException("The SingleEntityChildModel field should only return a single child entity.");
        }
        return childEntityFieldDefinition.evolveParentBasedOnChildInput(entity, evolvedChildEntities.getFirst());
    }

    @Nonnull
    @Override
    public EntityModel<C> entityModel() {
        return childEntityModel;
    }

    @Override
    public String toString() {
        return "SingleEntityChildModel{entityType=" + entityType().getName() + '}';
    }

    /**
     * Creates a new {@link Builder} for the given parent class and child entity model. The
     * {@link ChildEntityFieldDefinition} is required to resolve the child entity from the parent entity and evolve the
     * parent entity based on the child entities.
     *
     * @param parentClass The class of the parent entity.
     * @param entityModel The {@link EntityModel} of the child entity.
     * @param <C>         The type of the child entity.
     * @param <P>         The type of the parent entity.
     * @return A new {@link Builder} for the given parent class and child entity model.
     */
    public static <C, P> Builder<C, P> forEntityModel(@Nonnull Class<P> parentClass,
                                                      @Nonnull EntityModel<C> entityModel) {
        return new Builder<>(parentClass, entityModel);
    }


    /**
     * Builder for creating a {@link SingleEntityChildModel} for the given parent class and child entity model. The
     * {@link ChildEntityFieldDefinition} is required to resolve the child entities from the parent entity and evolve
     * the parent entity based on the child entities.
     * <p>
     * The {@link CommandTargetResolver} and {@link EventTargetMatcher} are defaulted to
     * {@link CommandTargetResolver#MATCH_ANY()} and {@link EventTargetMatcher#MATCH_ANY()} respectively, meaning that
     * the child entity will always match all commands and all events. If you have multiple member fields, and want to
     * match commands and events to a specific child entity, you can configure the {@link CommandTargetResolver} and
     * {@link EventTargetMatcher} to match the child entity based on the command or event.
     *
     * @param <C> The type of the child entity.
     * @param <P> The type of the parent entity.
     */
    public static class Builder<C, P> extends AbstractEntityChildModel.Builder<C, P, Builder<C, P>> {

        private ChildEntityFieldDefinition<P, C> childEntityFieldDefinition;

        @SuppressWarnings("unused") // Uses for generics
        private Builder(@Nonnull Class<P> parentClass,
                        @Nonnull EntityModel<C> childEntityModel
        ) {
            super(parentClass, childEntityModel);
            this.commandTargetResolver = CommandTargetResolver.MATCH_ANY();
            this.eventTargetMatcher = EventTargetMatcher.MATCH_ANY();
        }

        /**
         * Sets the {@link ChildEntityFieldDefinition} to use for resolving the child entity from the parent entity and
         * evolving the parent entity based on the evolved child entity.
         *
         * @param fieldDefinition The {@link ChildEntityFieldDefinition} to use for resolving the child entities from
         *                        the parent entity
         * @return This builder instance.
         */
        public Builder<C, P> childEntityFieldDefinition(@Nonnull ChildEntityFieldDefinition<P, C> fieldDefinition) {
            this.childEntityFieldDefinition = Objects.requireNonNull(fieldDefinition,
                                                                     "The fieldDefinition may not be null.");
            return this;
        }

        /**
         * Builds a new {@link SingleEntityChildModel} instance with the configured properties. The
         * {@link ChildEntityFieldDefinition} is required to be set before calling this method.
         *
         * @return A new {@link SingleEntityChildModel} instance with the configured properties.
         */
        public SingleEntityChildModel<C, P> build() {
            this.validate();
            return new SingleEntityChildModel<>(childEntityModel,
                                                childEntityFieldDefinition,
                                                commandTargetResolver,
                                                eventTargetMatcher
            );
        }
    }
}
