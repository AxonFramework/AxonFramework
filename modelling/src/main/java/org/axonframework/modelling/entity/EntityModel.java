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

package org.axonframework.modelling.entity;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityEvolver;

import java.util.Set;

/**
 * Interface describing the model of an entity. It contains the information needed to handle commands and events for a
 * specific entity type. A new {@link EntityModel} can be created using the {@link #forEntityType(Class)} method. The
 * model can then be used to handle commands and events for an entity instance through the {@link #handle} and
 * {@link #evolve} methods.
 *
 * @param <E> The type of the entity modeled by this interface.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface EntityModel<E> extends EntityEvolver<E> {

    /**
     * Returns the {@link Class} of the entity this model describes.
     *
     * @return The {@link Class} of the entity this model describes.
     */
    Class<E> entityType();

    /**
     * Returns the {@link EntityEvolver} for this model. This is used to evolve the entity state based on events.
     *
     * @param message The {@link CommandMessage} to handle.
     * @param entity  The entity instance to handle the command for.
     * @param context The {@link ProcessingContext} for the command.
     * @return The {@link EntityEvolver} for this model.
     */
    MessageStream.Single<? extends CommandResultMessage<?>> handle(
            CommandMessage<?> message, E entity, ProcessingContext context
    );

    /**
     * Returns the set of all {@link QualifiedName QualifiedNames} that this model supports for command handlers.
     *
     * @return A set of {@link QualifiedName} instances representing the supported command names.
     */
    Set<QualifiedName> supportedCommands();

    /**
     * Starts a new {@link EntityModelBuilder} for the given entity type. The builder can be used to add command
     * handlers and child entities to the model.
     *
     * @param entityType The type of the entity to create a model for.
     * @param <E>        The type of the entity to create a model for.
     * @return A new {@link EntityModelBuilder} for the given entity type.
     */
    static <E> EntityModelBuilder<E> forEntityType(Class<E> entityType) {
        return SimpleEntityModel.forEntityClass(entityType);
    }
}
