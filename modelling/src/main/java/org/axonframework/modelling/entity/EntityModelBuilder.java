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
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.child.EntityChildModel;
import org.axonframework.modelling.entity.child.ListEntityChildModel;
import org.axonframework.modelling.entity.child.SingleEntityChildModel;

/**
 * Builder for a {@link EntityModel} instance. This builder allows for the configuration of the entity model, including
 * the addition of command handlers and child entity models.
 *
 * @param <E> The type of the entity modeled by this interface.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface EntityModelBuilder<E> {

    /**
     * Adds an {@link EntityCommandHandler} to this model for the given {@link QualifiedName}. The command handler will
     * be invoked when a command with the given {@link QualifiedName} is received by the model.
     *
     * @param qualifiedName  The {@link QualifiedName} of the command this handler handles.
     * @param messageHandler The {@link EntityCommandHandler} to handle the command.
     * @return This builder for further configuration.
     */
    @Nonnull
    EntityModelBuilder<E> commandHandler(@Nonnull QualifiedName qualifiedName, @Nonnull EntityCommandHandler<E> messageHandler);

    /**
     * Adds a child {@link EntityChildModel} to this model. The child model will be used to handle commands for the
     * child entity. You can build a tree of entities by adding child models to the parent model. Children command
     * handlers take precedence over the parent command handlers. Event handlers will be invoked on both the parent and
     * child models, but the child models will be invoked first.
     * <p>
     * There are various types of children that can be added to an entity model:
     * <ul>
     *     <li>Single instances: For a field with a single instance, use the {@link SingleEntityChildModel}.</li>
     *     <li>Single instances: For a {@link java.util.List list}, use the {@link ListEntityChildModel}.</li>
     * </ul>
     *
     * @param child The {@link EntityChildModel} to add.
     * @return This builder for further configuration.
     */
    @Nonnull
    EntityModelBuilder<E> addChild(@Nonnull EntityChildModel<?, E> child);

    /**
     * Adds a {@link EntityEvolver} to this model. This evolver will be called upon applying an event to the entity. The
     * evolver is responsible for evolving the entity state based on the event. Note that this is optional. However, if
     * no evolver is provided, the entity state can only be changed through command handlers.
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