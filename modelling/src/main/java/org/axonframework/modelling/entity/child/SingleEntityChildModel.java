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
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.EntityModel;

import java.util.Set;

/**
 * An {@link EntityChildModel} that handles commands and events for a single child entity. It will use the provided
 * {@link ChildEntityFieldDefinition} to resolve the child entity from the parent entity. Once the entity is resolved,
 * it will delegate the command- and event-handling to the child entity model.
 * <p>
 * If the child entity is not present in the parent entity, an {@link IllegalArgumentException} will be thrown.
 *
 * @param <C> The type of the child entity.
 * @param <P> The type of the parent entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class SingleEntityChildModel<C, P> implements EntityChildModel<C, P> {

    private final EntityModel<C> childEntityModel;
    private final ChildEntityFieldDefinition<P, C> childEntityFieldDefinition;

    private SingleEntityChildModel(EntityModel<C> childEntityModel,
                                   ChildEntityFieldDefinition<P, C> childEntityFieldDefinition) {
        this.childEntityModel = childEntityModel;
        this.childEntityFieldDefinition = childEntityFieldDefinition;
    }

    public Set<QualifiedName> supportedCommands() {
        return childEntityModel.supportedCommands();
    }


    @Override
    public P evolve(@Nonnull P entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        C childEntity = childEntityFieldDefinition.getChildEntities(entity);
        if (childEntity != null) {
            C evolvedEntity = childEntityModel.evolve(childEntity, event, context);
            return childEntityFieldDefinition.evolveParentBasedOnChildEntities(entity, evolvedEntity);
        }
        return entity;
    }

    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                          P entity,
                                                                          ProcessingContext context) {
        C childEntity = childEntityFieldDefinition.getChildEntities(entity);
        if (childEntity == null) {
            throw new IllegalArgumentException(
                    "No child entity found for command " + message.type().qualifiedName()
                            + " on parent entity " + entity
            );
        }
        return childEntityModel.handle(message, childEntity, context);
    }

    @Override
    public Class<C> entityType() {
        return childEntityModel.entityType();
    }

    /**
     * Creates a new {@link Builder} for the given parent class and child entity model. The
     * {@link ChildEntityFieldDefinition} is required to resolve the child entity from the parent entity and evolve the
     * parent entity based on the child entities. events.
     *
     * @param parentClass The class of the parent entity.
     * @param entityModel The {@link EntityModel} of the child entity.
     * @param <C>         The type of the child entity.
     * @param <P>         The type of the parent entity.
     * @return A new {@link Builder} for the given parent class and child entity model.
     */
    public static <C, P> Builder<C, P> forEntityClass(Class<P> parentClass,
                                                      EntityModel<C> entityModel) {
        return new Builder<>(parentClass, entityModel);
    }


    /**
     * Builder for creating a {@link SingleEntityChildModel} for the given parent class and child entity model. The
     * {@link ChildEntityFieldDefinition} is required to resolve the child entities from the parent entity and evolve
     * the parent entity based on the child entities. T
     *
     * @param <C> The type of the child entity.
     * @param <P> The type of the parent entity.
     */
    public static class Builder<C, P> {

        private final EntityModel<C> childEntityModel;
        private ChildEntityFieldDefinition<P, C> childEntityFieldDefinition;

        @SuppressWarnings("unused") // Uses for generics
        private Builder(@Nonnull Class<P> parentClass,
                        @Nonnull EntityModel<C> childEntityModel
        ) {
            this.childEntityModel = childEntityModel;
        }

        /**
         * Sets the {@link ChildEntityFieldDefinition} to use for resolving the child entity from the parent entity and
         * evolving the parent entity based on the evolved child entity.
         *
         * @param fieldDefinition The {@link ChildEntityFieldDefinition} to use for resolving the child entities from
         *                        the parent entity
         * @return This builder instance.
         */
        public Builder<C, P> childEntityFieldDefinition(ChildEntityFieldDefinition<P, C> fieldDefinition) {
            this.childEntityFieldDefinition = fieldDefinition;
            return this;
        }

        /**
         * Builds a new {@link SingleEntityChildModel} instance with the configured properties. The
         * {@link ChildEntityFieldDefinition} is required to be set before calling this method.
         *
         * @return A new {@link SingleEntityChildModel} instance with the configured properties.
         */
        public SingleEntityChildModel<C, P> build() {
            Assert.notNull(childEntityFieldDefinition, () -> "Child entity field definition is required");
            return new SingleEntityChildModel<>(childEntityModel,
                                                childEntityFieldDefinition
            );
        }
    }
}
