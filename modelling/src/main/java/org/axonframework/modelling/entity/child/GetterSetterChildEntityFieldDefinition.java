/*
 * Copyright (c) 2010-2026. Axon Framework
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

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Implementation of {@link ChildEntityFieldDefinition} that uses a getter and a setter to get the child entities from
 * the parent, and to set the evolved child entities on the parent entity.
 *
 * @param <P> The type of the parent entity.
 * @param <F> The type of the field. This can be the type of the child entity or a collection of child entities.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class GetterSetterChildEntityFieldDefinition<P, F> implements ChildEntityFieldDefinition<P, F> {

    private final Function<P, F> getter;
    private final BiConsumer<P, F> setter;

    /**
     * Creates a new {@link ChildEntityFieldDefinition} that uses the given getter and setter to access the
     * child entity and set the evolved child entities on the parent entity.
     *
     * @param getter  the getter to access the child entity.
     * @param setter the setter to set the evolved child entity on the parent entity.
     */
    public GetterSetterChildEntityFieldDefinition(
            @Nonnull Function<P, F> getter,
            @Nonnull BiConsumer<P, F> setter
    ) {
        this.getter = Objects.requireNonNull(getter, "The getter may not be null.");
        this.setter = Objects.requireNonNull(setter, "The setter may not be null.");
    }

    @Nonnull
    @Override
    public P evolveParentBasedOnChildInput(@Nonnull P parentEntity, @Nonnull F childInput) {
        setter.accept(parentEntity, childInput);
        return parentEntity;
    }

    @Override
    public F getChildValue(@Nonnull P parentEntity) {
        return getter.apply(parentEntity);
    }
}
