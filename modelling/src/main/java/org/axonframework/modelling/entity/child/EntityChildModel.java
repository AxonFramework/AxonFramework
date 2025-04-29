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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.EntityModel;

import java.util.Objects;
import java.util.Set;

/**
 * Interface describing a child {@link EntityModel} that can be handled in the context of its parent. Handling commands
 * for this model is done in the context of the parent. This model resolves the child from the given parent and can
 * then invoke the right child instance to handle the command.
 *
 * @param <C> The type of the child entity.
 * @param <P> The type of the parent entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface EntityChildModel<C, P> extends EntityEvolver<P> {

    /**
     * Returns the set of all {@link QualifiedName QualifiedNames} that this model supports for command handlers.
     *
     * @return A set of {@link QualifiedName} instances representing the supported command names.
     */
    Set<QualifiedName> supportedCommands();

    /**
     * Checks if this child can handle the given {@link CommandMessage} for the given parent entity, and a child entity
     * is available to handle it.
     *
     * @param message      The {@link CommandMessage} to check.
     * @param parentEntity The parent entity instance to check against.
     * @param context      The {@link ProcessingContext} for the command.
     * @return {@code true} if this child can handle the command, {@code false} otherwise.
     */
    boolean canHandle(@Nonnull CommandMessage<?> message, @Nonnull P parentEntity, @Nonnull ProcessingContext context);

    /**
     * Handles the given {@link CommandMessage} for the given child entity, using the provided parent entity.
     *
     * @param message The {@link CommandMessage} to handle.
     * @param entity  The child entity instance to handle the command for.
     * @param context The {@link ProcessingContext} for the command.
     * @return The result of the command handling, which may be a {@link CommandResultMessage} or an error message.
     */
    MessageStream.Single<CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> message,
                                                         @Nonnull P entity,
                                                         @Nonnull ProcessingContext context);

    /**
     * Returns the {@link Class} of the child entity this model describes.
     *
     * @return The {@link Class} of the child entity this model describes.
     */
    Class<C> entityType();

    /**
     * Starts a builder for a single child entity within the given parent entity type.
     *
     * @param parentClass      The class of the parent entity.
     * @param childEntityModel The {@link EntityModel} of the child entity.
     * @param <C>              The type of the child entity.
     * @param <P>              The type of the parent entity.
     * @return A {@link SingleEntityChildModel.Builder} for the child entity.
     */
    static <C, P> SingleEntityChildModel.Builder<C, P> single(@Nonnull Class<P> parentClass,
                                                              @Nonnull EntityModel<C> childEntityModel) {
        return SingleEntityChildModel.forEntityModel(
                Objects.requireNonNull(parentClass, "parentClass may not be null"),
                Objects.requireNonNull(childEntityModel, "childEntityModel may not be null")
        );
    }

    /**
     * Starts a builder for a list of child entities within the given parent entity type.
     *
     * @param parentClass      The class of the parent entity.
     * @param childEntityModel The {@link EntityModel} of the child entity.
     * @param <C>              The type of the child entity.
     * @param <P>              The type of the parent entity.
     * @return A {@link ListEntityChildModel.Builder} for the child entity.
     */
    static <C, P> ListEntityChildModel.Builder<C, P> list(Class<P> parentClass, EntityModel<C> childEntityModel) {
        return ListEntityChildModel.forEntityModel(
                Objects.requireNonNull(parentClass, "parentClass may not be null"),
                Objects.requireNonNull(childEntityModel, "childEntityModel may not be null")
        );
    }
}
