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

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityEvolver;

import java.util.Set;

/**
 * The model of an entity, containing the information needed to handle commands and events for a specific entity type
 * {@code E}. An {@link EntityModel} can be created through the builder by using the {@link #forEntityType(Class)}
 * method. The model can then be used to handle commands and events for an entity instance through the
 * {@link #handleInstance} and {@link #evolve} methods.
 *
 * @param <E> The type of the entity modeled by this interface.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface EntityModel<E> extends EntityEvolver<E>, DescribableComponent {

    /**
     * Returns the {@link Class} of the entity this model describes.
     *
     * @return The {@link Class} of the entity this model describes.
     */
    @Nonnull
    Class<E> entityType();

    /**
     * Handles the given {@link CommandMessage} as the creation of a new entity. It is up to the registered command
     * handler to create the entity.
     * <p>
     * This method is used to handle commands for new entities. If you want to handle commands for existing entities,
     * use the {@link #handleInstance} method instead. If the command handler is only known as a creational command
     * handler and this method is called, it will result in a failed message stream.
     *
     * @param message The {@link CommandMessage} to handle.
     * @param context The {@link ProcessingContext} for the command.
     * @return A stream with a message containing the result of the command handling, which may be a
     * {@link CommandResultMessage} or an error message.
     */
    @Nonnull
    MessageStream.Single<CommandResultMessage<?>> handleCreate(
            CommandMessage<?> message, ProcessingContext context
    );

    /**
     * Handles the given {@link CommandMessage} for the given {@code entity}. If any of its children can handle the
     * command, it will be delegated to them. Otherwise, the command will be handled by this model. If the command is
     * not handled by this model or any of its children, an {@link MessageStream#failed(Throwable)} will be returned.
     * <p>
     * This method is used to handle commands for existing entities. If you want to handle commands for new entities,
     * use the {@link #handleCreate} method instead. If the command handler is only known as a creational command
     * handler and this method is called, it will result in a failed message stream.
     *
     * @param message The {@link CommandMessage} to handle.
     * @param entity  The entity instance to handle the command for.
     * @param context The {@link ProcessingContext} for the command.
     * @return A stream with a message containing the result of the command handling, which may be a
     * {@link CommandResultMessage} or an error message.
     */
    @Nonnull
    MessageStream.Single<CommandResultMessage<?>> handleInstance(
            @Nonnull CommandMessage<?> message, @Nonnull E entity, @Nonnull ProcessingContext context
    );

    /**
     * Returns the set of all {@link QualifiedName QualifiedNames} that this model supports for creating entities. These
     * are the commands types that can be used to create an entity of this type through the {@link #handleCreate}
     * method.
     *
     * @return A set of {@link QualifiedName} instances representing the supported command names.
     */
    @Nonnull
    Set<QualifiedName> supportedCreationalCommands();

    /**
     * Returns the set of all {@link QualifiedName QualifiedNames} that this model supports for instance commands. These
     * are the command types that can be used on entity instances of this type through the {@link #handleInstance}
     * method.
     *
     * @return A set of {@link QualifiedName} instances representing the supported command names.
     */
    @Nonnull
    Set<QualifiedName> supportedInstanceCommands();

    /**
     * Returns the set of all {@link QualifiedName QualifiedNames} that this model supports for command handlers, both
     * creational and instance commands. This is the union of the {@link #supportedCreationalCommands()} and
     * {@link #supportedInstanceCommands()} methods.
     *
     * @return A set of {@link QualifiedName} instances representing the supported command names.
     */
    @Nonnull
    Set<QualifiedName> supportedCommands();

    /**
     * Starts a new {@link EntityModelBuilder} for the given entity type. The builder can be used to add command
     * handlers and child entities to the model.
     *
     * @param entityType The type of the entity to create a model for.
     * @param <E>        The type of the entity to create a model for.
     * @return A new {@link EntityModelBuilder} for the given entity type.
     */
    @Nonnull
    static <E> EntityModelBuilder<E> forEntityType(Class<E> entityType) {
        return SimpleEntityModel.forEntityClass(entityType);
    }
}
