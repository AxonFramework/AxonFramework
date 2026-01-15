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
import jakarta.annotation.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Functional interface describing how to get the child entity (or entity collection), and apply the evolved child
 * entity (or entities in case of a collection) to the parent entity. The function can simply set a field or return a
 * new instance of the parent entity with the evolved child entity. The value returned from this function will be used
 * as the new parent entity and must return a non-null value.
 * <p>
 * There are three default ways to create an implementation:
 * <ul>
 *     <li>Using a Getter and Setter: {@link #forGetterSetter(Function, BiConsumer)}.</li>
 *     <li>Using a Getter and Evolver: {@link #forGetterEvolver(Function, BiFunction)}. Works similarly to the Getter/Setter, but this Evolver returns a new version of the parent entity, supporting immutable objects.</li>
 *     <li>Using a field name: {@link #forFieldName(Class, String)}. This will use reflection to get/set the field on the parent entity. Will automatically use getters, setters, and evolving methods if available.</li>
 * </ul>
 *
 * @param <P> The type of the parent entity.
 * @param <F> The type of the field. This can be the type of the child entity or a collection of child entities.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface ChildEntityFieldDefinition<P, F> {

    /**
     * Evolves the parent entity based on the provided child value. This can be a single entity, or a collection of
     * entities. The evolver can return a new version of the parent entity, or it can simply set the field on the parent
     * entity.
     *
     * @param parentEntity The parent entity to evolve.
     * @param childInput   The child entity to use for evolution.
     * @return The evolved parent entity.
     */
    @Nonnull
    P evolveParentBasedOnChildInput(@Nonnull P parentEntity, @Nullable F childInput);

    /**
     * Returns the type of the field.
     *
     * @param parentEntity The parent entity to get the child entities from.
     * @return The type of the field.
     */
    @Nullable
    F getChildValue(@Nonnull P parentEntity);

    /**
     * Creates a new {@link FieldChildEntityFieldDefinition} for the given field name. This will use reflection to
     * get/set the field on the parent entity, and will automatically use getters, setters, and evolving methods if
     * available.
     *
     * @param parentClass The class of the parent entity.
     * @param fieldName   The name of the field to get/set on the parent entity.
     * @param <P>         The type of the parent entity.
     * @param <F>         The type of the field.
     * @return A new {@link FieldChildEntityFieldDefinition} for the given field name.
     */
    @Nonnull
    static <P, F> ChildEntityFieldDefinition<P, F> forFieldName(
            @Nonnull Class<P> parentClass,
            @Nonnull String fieldName
    ) {
        return new FieldChildEntityFieldDefinition<>(parentClass, fieldName);
    }

    /**
     * Creates a new {@link GetterEvolverChildEntityFieldDefinition} for the given getter and evolver. The evolver will
     * be used to create a new version of the parent entity on which the new child entities are set. The getter will be
     * used to get the field from the parent entity.
     * <p>
     * An example of a compatible method can be seen below:
     * <pre>
     *     public record ParentEntity(List<ChildEntity> childEntities) {
     *         public ParentEntity evolveChildEntities(List<ChildEntity> childEntities) {
     *             return new ParentEntity(childEntities);
     *         }
     *     }
     * </pre>
     * <p>
     * The field definition for this looks as follows:
     * <pre>
     *     ChildEntityFieldDefinition.forGetterEvolver(
     *        ParentEntity::childEntities,
     *        ParentEntity::evolveChildEntities
     *     );
     * </pre>
     *
     * @param getter  The getter to get the field from the parent entity.
     * @param evolver The evolver to set the field on the parent entity.
     * @param <P>     The type of the parent entity.
     * @param <F>     The type of the field.
     * @return A new {@link GetterEvolverChildEntityFieldDefinition} for the given getter and setter.
     */
    @Nonnull
    static <P, F> ChildEntityFieldDefinition<P, F> forGetterEvolver(
            @Nonnull Function<P, F> getter,
            @Nonnull BiFunction<P, F, P> evolver
    ) {
        return new GetterEvolverChildEntityFieldDefinition<>(getter, evolver);
    }

    /**
     * Creates a new {@link GetterSetterChildEntityFieldDefinition} for the given getter and setter. The setter will be
     * used to set the field on the parent entity, and the setter will be used to set the field on the parent entity.
     *
     * @param getter The getter to get the field from the parent entity.
     * @param setter The setter to set the field on the parent entity.
     * @param <P>    The type of the parent entity.
     * @param <F>    The type of the field.
     * @return A new {@link GetterSetterChildEntityFieldDefinition} for the given getter and setter.
     */
    @Nonnull
    static <P, F> ChildEntityFieldDefinition<P, F> forGetterSetter(
            @Nonnull Function<P, F> getter,
            @Nonnull BiConsumer<P, F> setter
    ) {
        return new GetterSetterChildEntityFieldDefinition<>(getter, setter);
    }
}
