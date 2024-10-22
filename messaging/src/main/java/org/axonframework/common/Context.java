/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.common;

import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Interface describing operations for context-specific resource management.
 * <p>
 * It is recommended to construct a {@link ResourceKey} instance when adding/updating/removing resources from the
 * {@link Context} to allow cross-referral by sharing the key or personalization when the resource should be private to
 * a specific service.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savić
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface Context {

    /**
     * Indicates whether a resource has been registered with the given {@code key} in this {@link Context}.
     *
     * @param key The key of the resource to check.
     * @return {@code true} if a resource is registered with this {@code key}, otherwise {@code false}.
     */
    boolean containsResource(@Nonnull ResourceKey<?> key);

    /**
     * Returns the resource currently registered under the given {@code key}, or {@code null} if no resource is
     * present.
     *
     * @param key The key to retrieve the resource for.
     * @param <T> The type of resource registered under the given {@code key}.
     * @return The resource currently registered under given {@code key}, or {@code null} if not present.
     */
    <T> T getResource(@Nonnull ResourceKey<T> key);

    /**
     * Register the given {@code resource} under the given {@code key}.
     *
     * @param key      The key under which to register the {@code resource}.
     * @param resource The resource to register.
     * @param <T>      The type of {@code resource} to register under given @code.
     * @return The previously registered {@code resource}, or {@code null} if none was present.
     */
    <T> T putResource(@Nonnull ResourceKey<T> key, @Nonnull T resource);

    /**
     * Add all the resources from the given {@code context} into {@code this} {@link Context}.
     *
     * @param context The {@link Context} for which to copy the contents over to {@code this} {@link Context}.
     */
    void putAll(@Nonnull Context context);

    /**
     * Update the resource with given {@code key} using the given {@code resourceUpdater} to describe the update. If no
     * resource is registered with the given {@code key}, the {@code resourceUpdater} is invoked with {@code null}.
     * Otherwise, the function is called with the currently registered resource under that key.
     * <p>
     * The resource is replaced with the return value of the function, or removed when the function returns
     * {@code null}.
     * <p>
     * If the function throws an exception, the exception is rethrown to the caller.
     *
     * @param key             The key to update the resource for.
     * @param resourceUpdater The function performing the update itself.
     * @param <T>             The type of resource to update.
     * @return The new value associated with the {@code key}, or {@code null} when removed.
     */
    <T> T updateResource(@Nonnull ResourceKey<T> key, @Nonnull UnaryOperator<T> resourceUpdater);

    /**
     * Register the given {@code instance} under the given {@code key} if no value is currently present.
     *
     * @param key      The key under which to register the resource.
     * @param resource The resource to register when nothing is present for the given {@code key}.
     * @param <T>      The type of {@code resource} to register under given {@code key}.
     * @return The resource previously associated with given {@code key}.
     */
    <T> T putResourceIfAbsent(@Nonnull ResourceKey<T> key, @Nonnull T resource);

    /**
     * If no resource is present for the given {@code key}, the given {@code resourceSupplier} is used to supply the
     * instance to register under this {@code key}.
     *
     * @param key              The key to register the resource for.
     * @param resourceSupplier The function to supply the resource to register.
     * @param <T>              The type of resource registered under given {@code key}.
     * @return The resource associated with the {@code key}.
     */
    <T> T computeResourceIfAbsent(@Nonnull ResourceKey<T> key, @Nonnull Supplier<T> resourceSupplier);

    /**
     * Removes the resource registered under given {@code key}.
     *
     * @param key The key to remove the registered resource for.
     * @param <T> The type of resource associated with the {@code key}.
     * @return The value previously associated with the {@code key}.
     */
    <T> T removeResource(@Nonnull ResourceKey<T> key);

    /**
     * Remove the resource associated with given {@code key} if the given {@code expectedResource} is the currently
     * associated value.
     *
     * @param key              The key to remove the registered resource for.
     * @param expectedResource The expected resource to remove.
     * @param <T>              The type of resource associated with the {@code key}.
     * @return {@code true} if the resource has been removed, otherwise {@code false}.
     */
    <T> boolean removeResource(@Nonnull ResourceKey<T> key, @Nonnull T expectedResource);

    /**
     * Return all the resources contained in this {@link Context} as a {@link Map}.
     *
     * @return All the resources contained in this {@link Context} as a {@link Map}.
     */
    Map<ResourceKey<?>, ?> asMap();

    /**
     * Object that is used as a key to retrieve and register resources of a given type in a processing context.
     * <p>
     * Implementations are encouraged to override the {@link #toString()} method to include some information useful for
     * debugging.
     * <p>
     * Instance of a {@code ResourceKey} can be created using {@link ResourceKey#create(String)}.
     *
     * @param <T> The type of resource registered under this key.
     */
    @SuppressWarnings("unused") // Suppresses the warning that the generic type is not used.
    final class ResourceKey<T> {

        private static final String RESOURCE_KEY_PREFIX = "ResourceKey@";

        private final String toString;

        private ResourceKey(String debugString) {
            String keyId = RESOURCE_KEY_PREFIX + Integer.toHexString(System.identityHashCode(this));
            if (debugString == null || debugString.isBlank()) {
                this.toString = keyId;
            } else {
                this.toString = keyId + "[" + debugString + "]";
            }
        }

        /**
         * Create a new {@link ResourceKey} for a resource of type {@code T}. The given {@code debugString} is part of
         * the {@link #toString()} (if not {@code null} or empty) of the created key instance and may be used for
         * debugging purposes.
         *
         * @param debugString A {@link String} to recognize this key during debugging.
         * @param <T>         The type of resource of this key.
         * @return A new key used to register and retrieve resources.
         */
        public static <T> ResourceKey<T> create(String debugString) {
            return new ResourceKey<>(debugString);
        }

        @Override
        public String toString() {
            return toString;
        }
    }
}
