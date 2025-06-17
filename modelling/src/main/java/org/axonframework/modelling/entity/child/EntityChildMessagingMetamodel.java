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
import org.axonframework.modelling.entity.EntityMessagingMetamodel;

import java.util.Set;

/**
 * Interface describing a child {@link EntityMessagingMetamodel} that can be handled in the context of its parent.
 * Handling commands for this model is done in the context of the parent. This metamodel resolves the child from the
 * given parent and can then invoke the right child instance to handle the command.
 *
 * @param <C> The type of the child entity.
 * @param <P> The type of the parent entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface EntityChildMessagingMetamodel<C, P> extends EntityEvolver<P> {

    /**
     * Returns the set of all {@link QualifiedName QualifiedNames} that this metamodel supports for command handlers.
     *
     * @return A set of {@link QualifiedName} instances representing the supported command names.
     */
    @Nonnull
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
     * @param message      The {@link CommandMessage} to handle.
     * @param parentEntity The child entity instance to handle the command for.
     * @param context      The {@link ProcessingContext} for the command.
     * @return The result of the command handling, which may be a {@link CommandResultMessage} or an error message.
     */
    @Nonnull
    MessageStream.Single<CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> message,
                                                         @Nonnull P parentEntity,
                                                         @Nonnull ProcessingContext context);

    /**
     * Returns the {@link Class} of the child entity this metamodel describes.
     *
     * @return The {@link Class} of the child entity this metamodel describes.
     */
    @Nonnull
    Class<C> entityType();

    /**
     * Returns the {@link EntityMessagingMetamodel} of the child entity this metamodel describes.
     *
     * @return The {@link EntityMessagingMetamodel} of the child entity this metamodel describes.
     */
    @Nonnull
    EntityMessagingMetamodel<C> entityMetamodel();

    /**
     * Starts a builder for a single child entity within the given parent entity type.
     *
     * @param parentClass The class of the parent entity.
     * @param metamodel   The {@link EntityMessagingMetamodel} of the child entity.
     * @param <C>         The type of the child entity.
     * @param <P>         The type of the parent entity.
     * @return A {@link SingleEntityChildMessagingMetamodel.Builder} for the child entity.
     */
    @Nonnull
    static <C, P> SingleEntityChildMessagingMetamodel.Builder<C, P> single(
            @Nonnull Class<P> parentClass,
            @Nonnull EntityMessagingMetamodel<C> metamodel) {
        return SingleEntityChildMessagingMetamodel.forEntityModel(parentClass, metamodel);
    }

    /**
     * Starts a builder for a list of child entities within the given parent entity type.
     *
     * @param parentClass The class of the parent entity.
     * @param metamodel   The {@link EntityMessagingMetamodel} of the child entity.
     * @param <C>         The type of the child entity.
     * @param <P>         The type of the parent entity.
     * @return A {@link ListEntityChildMessagingMetamodel.Builder} for the child entity.
     */
    @Nonnull
    static <C, P> ListEntityChildMessagingMetamodel.Builder<C, P> list(
            @Nonnull Class<P> parentClass,
            @Nonnull EntityMessagingMetamodel<C> metamodel) {
        return ListEntityChildMessagingMetamodel.forEntityModel(parentClass, metamodel);
    }
}
