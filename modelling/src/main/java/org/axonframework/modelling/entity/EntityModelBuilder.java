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
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.child.EntityChildModel;
import org.axonframework.modelling.repository.Repository;

/**
 * Builder for a {@link EntityModel} instance. This builder allows for the configuration of the entity model, including
 * the addition of command handlers and child entity models.
 * <p>
 * Entity models can handle two types of commands:
 * <ul>
 *     <li>Instance commands: Commands that are handled by an existing entity. These commands are registered by the
 *     {@link #instanceCommandHandler(QualifiedName, EntityCommandHandler)} method and will be invoked through {@link EntityModel#handleInstance(CommandMessage, Object, ProcessingContext)}.
 *     These are comparable with static methods on an entity.</li>
 *     <li>Creational commands: Commands that are handled by a new entity. These commands are registered by the
 *     {@link #creationalCommandHandler(QualifiedName, CommandHandler)} method and will be invoked through {@link EntityModel#handleCreate(CommandMessage, ProcessingContext)}.
 *     These are comparable with class instance methods on an entity.</li>
 * </ul>
 * <p>
 * You can also register the same {@link QualifiedName} for both instance and creational command handlers. In that case, the
 * instance command handler will be invoked when {@link EntityModel#handleInstance(CommandMessage, Object, ProcessingContext)} is invoked, and the creational command handler will be
 * invoked when {@link EntityModel#handleCreate(CommandMessage, ProcessingContext)} is invoked.
 * <p>
 * Upon a mismatch a {@link RuntimeException} will be thrown:
 * <ul>
 *     <li>If an instance command is invoked for a non-existing entity, an {@link EntityMissingForInstanceCommandHandler}
 *     will be thrown.</li>
 *     <li>If a creational command is invoked for an existing entity, an {@link EntityExistsForCreationalCommandHandler}
 *     will be thrown.</li>
 * </ul>
 *
 * @param <E> The type of the entity modeled by this interface.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface EntityModelBuilder<E> {

    /**
     * Adds an {@link EntityCommandHandler} to this model for the given {@link QualifiedName}. The command handler will
     * be invoked when a command with the given {@link QualifiedName} is received by the model.
     * <p>
     * The entity should <b>not</b> exist for this command handler to be invoked. A non-null initial state is considered
     * to be an existing entity. As such, only register this command if the
     * {@link Repository#loadOrCreate(Object, ProcessingContext)} will always return a non-null entity for the given
     * {@code qualifiedName} when the entity is not yet created.
     * <p>
     * You can register the same {@link QualifiedName} for both instance and creational command handlers. See the
     * {@link EntityModelBuilder} class documentation for more information on how this works.
     *
     * @param qualifiedName  The {@link QualifiedName} of the command this handler handles.
     * @param messageHandler The {@link EntityCommandHandler} to handle the command.
     * @return This builder for further configuration.
     */
    @Nonnull
    EntityModelBuilder<E> instanceCommandHandler(@Nonnull QualifiedName qualifiedName,
                                                 @Nonnull EntityCommandHandler<E> messageHandler);

    /**
     * Adds a {@link CommandHandler} to this model for the given {@link QualifiedName} that is in charge of creation of
     * the entity. The handler is expected to create the entity.
     * <p>
     * The entity needs to not exist for this command handler to be invoked. A null initial state is considered to be a
     * non-existing entity. As such, only register this command if the
     * {@link Repository#load(Object, ProcessingContext)} will always return a null entity for the given
     * {@code qualifiedName} when the entity is not yet created.
     * <p>
     * You can register the same {@link QualifiedName} for both instance and creational command handlers. See the
     * {@link EntityModelBuilder} class documentation for more information on how this works.
     * <p>
     * Note: If this model is added as a child to another entity model and has a creational command handler, it will
     * result in an exception as child entities cannot be created through a creational command handler.
     *
     * @param qualifiedName  The {@link QualifiedName} of the command this handler handles.
     * @param messageHandler The {@link CommandHandler} to handle the command.
     * @return This builder for further configuration.
     */
    @Nonnull
    EntityModelBuilder<E> creationalCommandHandler(@Nonnull QualifiedName qualifiedName,
                                                   @Nonnull CommandHandler messageHandler);

    /**
     * Adds a {@link EntityChildModel} to this model. The child model will be used to handle commands for the child
     * entity. You can build a tree of entities by adding child models to the parent model. Children command handlers
     * take precedence over the parent command handlers. Event handlers will be invoked on both the parent and child
     * models, but the child models will be invoked first.
     * <p>
     * There are various types of children that can be added to an entity model:
     * <ul>
     *     <li>Single instances: For a field with a single instance, use the {@link EntityChildModel#single(Class, EntityModel)}.</li>
     *     <li>List instances: For a {@link java.util.List list}, use the {@link EntityChildModel#list(Class, EntityModel)}.</li>
     * </ul>
     * <p>
     * When multiple children that can handle the same command are present, the children will be filtered based on
     * {@link EntityChildModel#canHandle(CommandMessage, Object, ProcessingContext)}, and thus only invoke the child
     * with a matching entity. If no child can handle the command, an exception will be thrown. If after
     * filtering, multiple children can handle the command, an exception will be thrown.
     *
     * @param child The {@link EntityChildModel} to add.
     * @return This builder for further configuration.
     */
    @Nonnull
    EntityModelBuilder<E> addChild(@Nonnull EntityChildModel<?, E> child);

    /**
     * Adds a {@link EntityEvolver} to this model. This evolver will be called upon applying an event to the entity. The
     * evolver is responsible for evolving the entity state based on the event. Note that providing an evolver is
     * optional. However, if no evolver is provided, the entity state can only be changed through command handlers.
     * <p>
     * Calling this method a second time will override the previously set evolver.
     *
     * @param entityEvolver The {@link EntityEvolver} to use.
     * @return This builder for further configuration.
     */
    @Nonnull
    EntityModelBuilder<E> entityEvolver(@Nullable EntityEvolver<E> entityEvolver);

    /**
     * Builds the {@link EntityModel} instance based on the configuration of this builder. This method should be called
     * after all configuration is done.
     *
     * @return The {@link EntityModel} instance based on the configuration of this builder.
     */
    @Nonnull
    EntityModel<E> build();
}