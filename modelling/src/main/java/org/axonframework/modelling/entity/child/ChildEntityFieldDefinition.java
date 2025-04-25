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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Functional interface describing how to apply the evolved child entity (or entities in case of a collection) to the
 * parent entity. The function can simply set a field, or return a new instance of the parent entity with the evolved
 * child entity. The value returned from this function will be used as the new parent entity, and must return a non-null
 * value.
 * <p>
 * There are three default ways to create an implementation:
 * <ul>
 *     <li>Using a Getter and Setter: {@link #forGetterSetter(Function, BiConsumer)}</li>
 *     <li>Using a Getter and Evolver: {@link #forGetterEvolver(Function, BiFunction)}. It's similar to the Getter/Setter, but this Setter returns a new version of the parent entity, supporting immutable objects</li>
 *     <li>Using a field name: {@link #forFieldName(Class, String)}. This will use reflection to get/set the field on the parent entity. Will automatically use getters, setters, and evolving methods if available.</li>
 * </ul>
 *
 * @param <P> The type of the parent entity.
 * @param <F> The type of field.
 */
public interface ChildEntityFieldDefinition<P, F> {

    /**
     * Evolves the parent entity based on the provided child.
     *
     * @param parentEntity The parent entity to evolve.
     * @param result       The child entity to use for evolution.
     * @return The evolved parent entity.
     */
    P evolveParentBasedOnChildEntities(P parentEntity, F result);

    /**
     * Returns the type of the field.
     *
     * @param parentEntity The parent entity to get the child entities from.
     * @return The type of the field.
     */
    F getChildEntities(P parentEntity);

    /**
     * Creates a new {@link ChildEntityFieldDefinition} for the given field name. This will use reflection to get/set
     * the field on the parent entity.
     *
     * @param parentClass The class of the parent entity.
     * @param fieldName   The name of the field to get/set on the parent entity.
     * @param <P>         The type of the parent entity.
     * @param <F>         The type of the field.
     * @return A new {@link ChildEntityFieldDefinition} for the given field name.
     */
    static <P, F> ChildEntityFieldDefinition<P, F> forFieldName(
            Class<P> parentClass,
            String fieldName
    ) {
        return new FieldChildEntityFieldDefinition<>(parentClass, fieldName);
    }

    /**
     * Creates a new {@link ChildEntityFieldDefinition} for the given getter and setter. The setter will be used to set
     * the field on the parent entity, and the getter will be used to get the field from the parent entity.
     *
     * @param getter  The getter to get the field from the parent entity.
     * @param evolver The evolver to set the field on the parent entity.
     * @param <P>     The type of the parent entity.
     * @param <F>     The type of the field.
     * @return A new {@link ChildEntityFieldDefinition} for the given getter and setter.
     */
    static <P, F> ChildEntityFieldDefinition<P, F> forGetterEvolver(
            Function<P, F> getter,
            BiFunction<P, F, P> evolver
    ) {
        return new GetterEvolverChildEntityFieldDefinition<>(getter, evolver);
    }

    /**
     * Creates a new {@link ChildEntityFieldDefinition} for the given getter and setter. The setter will be used to set
     * the field on the parent entity, and the evolver will be used to get a new version of the parent entity on which
     * the new child entities are set.
     *
     * @param getter  The getter to get the field from the parent entity.
     * @param evolver The evolver to set the field on the parent entity.
     * @param <P>     The type of the parent entity.
     * @param <F>     The type of the field.
     * @return A new {@link ChildEntityFieldDefinition} for the given getter and setter.
     */
    static <P, F> ChildEntityFieldDefinition<P, F> forGetterSetter(
            Function<P, F> getter,
            BiConsumer<P, F> evolver
    ) {
        return new GetterSetterChildEntityFieldDefinition<>(getter, evolver);
    }
}
