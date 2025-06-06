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
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.child.EntityChildModel;

/**
 * Builder for a polymorphic {@link EntityModel}, where a parent entity can have multiple concrete child entities.
 * Command handlers of concrete types take precedence over the parent entity's command handlers. Event handlers are
 * invoked on both, with the super entity's event handlers being invoked first.
 *
 * @param <E> The type of the polymorphic entity this model represents.
 * @author Mitchell Herrijgers
 * @see PolymorphicEntityModel
 * @since 5.0.0
 */
public interface PolymorphicEntityModelBuilder<E> extends EntityModelBuilder<E> {

    @Nonnull
    @Override
    PolymorphicEntityModelBuilder<E> instanceCommandHandler(@Nonnull QualifiedName qualifiedName,
                                                            @Nonnull EntityCommandHandler<E> messageHandler);

    @Nonnull
    @Override
    PolymorphicEntityModelBuilder<E> creationalCommandHandler(@Nonnull QualifiedName qualifiedName,
                                                              @Nonnull CommandHandler messageHandler);

    @Nonnull
    @Override
    PolymorphicEntityModelBuilder<E> addChild(@Nonnull EntityChildModel<?, E> child);

    @Nonnull
    @Override
    PolymorphicEntityModelBuilder<E> entityEvolver(@Nullable EntityEvolver<E> entityEvolver);

    /**
     * Adds a concrete type to this polymorphic entity model. The concrete type must be a subclass of the parent entity
     * type.
     *
     * @param entityModel The {@link EntityModel} for the concrete type.
     * @return This builder for further configuration.
     */
    @Nonnull
    PolymorphicEntityModelBuilder<E> addConcreteType(@Nonnull EntityModel<? extends E> entityModel);
}
