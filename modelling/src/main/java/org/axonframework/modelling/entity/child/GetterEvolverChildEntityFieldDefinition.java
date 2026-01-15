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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Implementation of {@link ChildEntityFieldDefinition} that uses a getter and an evolver to get the child entities from
 * the parent, and to evolve the parent based on the child entities.
 *
 * @param <P> The type of the parent entity.
 * @param <F> The type of the field. This can be the type of the child entity or a collection of child entities.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class GetterEvolverChildEntityFieldDefinition<P, F> implements ChildEntityFieldDefinition<P, F> {

    private final Function<P, F> getter;
    private final BiFunction<P, F, P> evolver;

    /**
     * Creates a new {@link ChildEntityFieldDefinition} that uses the given getter and evolver to access
     * the child entity and evolve the parent entity.
     *
     * @param getter  the getter to access the child entity.
     * @param evolver the evolver to evolve the parent entity based on the child entity.
     */
    public GetterEvolverChildEntityFieldDefinition(
            @Nonnull Function<P, F> getter,
            @Nonnull BiFunction<P, F, P> evolver
    ) {
        this.getter = Objects.requireNonNull(getter, "The getter may not be null.");
        this.evolver = Objects.requireNonNull(evolver, "The evolver may not be null.");
    }

    @Nonnull
    @Override
    public P evolveParentBasedOnChildInput(@Nonnull P parentEntity, @Nonnull F childInput) {
        return evolver.apply(parentEntity, childInput);
    }

    @Override
    public F getChildValue(@Nonnull P parentEntity) {
        return getter.apply(parentEntity);
    }
}
