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

import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.child.EntityChildModel;

/**
 * Builder for a polymorphic entity model, where a parent entity can have multiple concrete child entities.
 *
 * @param <E>
 * @author Mitchell Herrijgers
 * @see PolymorphicEntityModel
 * @since 5.0.0
 */
public interface PolyMorphicEntityModelBuilder<E> extends EntityModelBuilder<E> {

    @Override
    PolyMorphicEntityModelBuilder<E> commandHandler(QualifiedName qualifiedName,
                                                    EntityCommandHandler<E> messageHandler);

    @Override
    PolyMorphicEntityModelBuilder<E> addChild(EntityChildModel<?, E> child);

    @Override
    PolyMorphicEntityModelBuilder<E> entityEvolver(EntityEvolver<E> entityEvolver);

    /**
     * Adds a concrete type to this polymorphic entity model. The concrete type must be a subclass of the parent entity
     * type.
     *
     * @param entityModel The {@link EntityModel} for the concrete type.
     * @return This builder for further configuration.
     */
    PolyMorphicEntityModelBuilder<E> addConcreteType(EntityModel<? extends E> entityModel);
}
