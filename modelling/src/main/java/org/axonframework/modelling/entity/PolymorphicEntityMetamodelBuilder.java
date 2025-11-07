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
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.child.EntityChildMetamodel;

/**
 * Builder for a polymorphic {@link EntityMetamodel}, where a parent entity can have multiple concrete child
 * entities. Command handlers of concrete types take precedence over the parent entity's command handlers. Event
 * handlers are invoked on both, with the super entity's event handlers being invoked first.
 *
 * @param <E> The type of the polymorphic entity this metamodel represents.
 * @author Mitchell Herrijgers
 * @see PolymorphicEntityMetamodel
 * @since 5.0.0
 */
public interface PolymorphicEntityMetamodelBuilder<E> extends EntityMetamodelBuilder<E> {

    @Nonnull
    @Override
    PolymorphicEntityMetamodelBuilder<E> instanceCommandHandler(@Nonnull QualifiedName qualifiedName,
                                                                @Nonnull EntityCommandHandler<E> messageHandler);

    @Nonnull
    @Override
    PolymorphicEntityMetamodelBuilder<E> creationalCommandHandler(@Nonnull QualifiedName qualifiedName,
                                                                  @Nonnull CommandHandler messageHandler);

    @Nonnull
    @Override
    PolymorphicEntityMetamodelBuilder<E> addChild(@Nonnull EntityChildMetamodel<?, E> child);

    @Nonnull
    @Override
    PolymorphicEntityMetamodelBuilder<E> entityEvolver(@Nullable EntityEvolver<E> entityEvolver);

    /**
     * Adds a concrete type to this metamodel. The concrete type must be a subclass of the parent entity
     * type.
     *
     * @param metamodel The {@link EntityMetamodel} for the concrete type.
     * @return This builder for further configuration.
     */
    @Nonnull
    PolymorphicEntityMetamodelBuilder<E> addConcreteType(
            @Nonnull EntityMetamodel<? extends E> metamodel);
}
