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

package org.axonframework.common.configuration;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.TypeReference;
import org.axonframework.common.infra.DescribableComponent;

import java.lang.reflect.Type;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.Assert.nonEmpty;

/**
 * Describes a component defined in a {@link Configuration}, that may depend on other component for its initialization
 * or during it's startup/shutdown operations.
 * <p>
 * Note: This interface is not expected to be used outside of Axon Framework!
 *
 * @param <C> The type of component.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
public interface Component<C> extends DescribableComponent {

    /**
     * The identifier of this component.
     *
     * @return The identifier of this component.
     */
    Identifier<C> identifier();

    /**
     * Resolves the instance of this component, allowing it to retrieve any of its required dependencies from the given
     * {@code configuration}.
     * <p>
     * Subsequent calls to this method will result in the same instance, even when <b>different</b> instances of
     * {@code configuration} are provided.
     *
     * @param configuration The configuration that declared this component.
     * @return The resolved instance defined in this component.
     */
    C resolve(@Nonnull Configuration configuration);

    /**
     * Indicates whether the component has been {@link #resolve(Configuration) resolved}.
     * <p>
     * When true, any subsequent call to {@link #resolve(Configuration)} will return that same instance.
     *
     * @return {@code true} if the component has been instantiated, otherwise {@code false}.
     */
    boolean isInstantiated();

    /**
     * Initializes the lifecycle handlers associated with this component.
     * <p>
     * Subsequent calls to this method will <b>not</b> result in additional invocations of the lifecycle handlers
     * registered with this component.
     *
     * @param configuration     The configuration in which the component was defined, allowing retrieval of dependencies
     *                          during the component's lifecycle.
     * @param lifecycleRegistry The registry in which to register the lifecycle handlers.
     */
    void initLifecycle(@Nonnull Configuration configuration,
                       @Nonnull LifecycleRegistry lifecycleRegistry);

    /**
     * Indicates whether the {@link #initLifecycle(Configuration, LifecycleRegistry)} method has already been invoked
     * for this component.
     *
     * @return {@code true} if the component's lifecycle has been initialized, otherwise {@code false}.
     */
    boolean isInitialized();

    /**
     * A tuple representing a {@code Component's} uniqueness, consisting out of a {@code type} and {@code name}.
     *
     * @param type The type reference of the component this object identifies.
     * @param name The name of the component this object identifies, potentially {@code null} when unimportant. Will
     *             throw an {@link IllegalArgumentException} for an empty {@code name}.
     * @param <C>  The type of the component this object identifies, typically an interface.
     */
    record Identifier<C>(@Nonnull TypeReference<C> type, @Nullable String name) {

        /**
         * A tuple representing a {@code Component's} uniqueness, consisting out of a {@code clazz} and {@code name}.
         *
         * @param clazz The clazz of the component this object identifies, typically an interface.
         * @param name  The name of the component this object identifies, potentially {@code null} when unimportant.
         *              Will throw an {@link IllegalArgumentException} for an empty {@code name}.
         */
        public Identifier(@Nonnull Class<C> clazz,
                          @Nullable String name) {
            this(TypeReference.fromClass(clazz), name);
        }

        /**
         * A tuple representing a {@code Component's} uniqueness, consisting out of a {@code type} and {@code name}.
         *
         * @param type The type of the component this object identifies, typically an interface.
         * @param name The name of the component this object identifies, potentially {@code null} when unimportant. Will
         *             throw an {@link IllegalArgumentException} for an empty {@code name}.
         */
        public Identifier(@Nonnull Type type,
                          @Nullable String name) {
            this(TypeReference.fromType(type), name);
        }

        /**
         * Compact constructor asserting whether the {@code type} and {@code name} are non-null and not empty.
         *
         * @param type The type of the component.
         * @param name The name of the component.
         */
        public Identifier {
            requireNonNull(type, "The given type is unsupported because it is null.");
            if (name != null) {
                nonEmpty(name, "The given name is unsupported because it is empty.");
            }
        }

        /**
         * Validate whether the given {@code other Identifier} matches with {@code this Identifier}, by matching the
         * {@link #name() names} and checking if the {@link #type()}  is {@link Class#isAssignableFrom(Class)} to give
         * {@code other} type.
         *
         * @param other The other identifier to compare with this identifier.
         * @return {@code true} if the {@link #type()} is assignable from the {@code other} type <b>and</b> the
         * {@link #name()} is identical, {@code false} otherwise.
         */
        public boolean matches(@Nonnull Identifier<?> other) {
            return matchesType(other) && Objects.equals(other.name(), name);
        }

        /**
         * Validate whether the given {@code other Identifier} type matches with {@code this Identifier} type, by
         * checking if the {@link #type()}  is {@link Class#isAssignableFrom(Class)} to give {@code other} type.
         *
         * @param other The other identifier to compare with this identifier.
         * @return {@code true} if the {@link #type()} is assignable from the {@code other} type, {@code false}
         * otherwise.
         */
        public boolean matchesType(@Nonnull Identifier<?> other) {
            return type.getTypeAsClass().isAssignableFrom(other.type().getTypeAsClass());
        }

        /**
         * Returns the {@link #type()} as a {@link Class}.
         * <p>
         * This means any generics present on the {@code type} are lost. When those are needed, be sure to use
         * {@link #type()} instead.
         *
         * @return The {@link #type()} as a {@link Class}.
         */
        public Class<C> typeAsClass() {
            return this.type.getTypeAsClass();
        }

        /**
         * Checks whether the {@link Class#getName()} of this {@code Identifier's} given {@code type} is equal to the
         * given {@code name}.
         *
         * @return {@code true} whenever the {@link Class#getName()} of this {@code Identifier's} given {@code type} is
         * equal to the given {@code name}, {@code false} otherwise.
         */
        public boolean areTypeAndNameEqual() {
            return type.getTypeAsClass().getName().equals(name);
        }

        @Override
        public String toString() {
            return type.getTypeAsClass().getName() + ":" + name;
        }
    }
}
