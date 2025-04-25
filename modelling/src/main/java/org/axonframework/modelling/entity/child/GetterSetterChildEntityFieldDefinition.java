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

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Implementation of {@link ChildEntityFieldDefinition} that uses a getter and a setter to get the child entities from
 * the parent, and to set the evolved child entities on the parent entity.
 *
 * @param <P> The type of the parent entity.
 * @param <F> The type of the child entity.
 */
public class GetterSetterChildEntityFieldDefinition<P, F> implements ChildEntityFieldDefinition<P, F> {

    private final Function<P, F> getter;
    private final BiConsumer<P, F> evolver;

    /**
     * Creates a new {@link GetterSetterChildEntityFieldDefinition} that uses the given getter and setter to access the
     * child entity and set the evolved child entities on the parent entity.
     *
     * @param getter  the getter to access the child entity.
     * @param evolver the setter to set the evolved child entity on the parent entity.
     */
    public GetterSetterChildEntityFieldDefinition(
            Function<P, F> getter,
            BiConsumer<P, F> evolver
    ) {
        this.getter = getter;
        this.evolver = evolver;
    }

    @Override
    public P evolveParentBasedOnChildEntities(P parentEntity, F result) {
        evolver.accept(parentEntity, result);
        return parentEntity;
    }

    @Override
    public F getChildEntities(P parentEntity) {
        return getter.apply(parentEntity);
    }
}
