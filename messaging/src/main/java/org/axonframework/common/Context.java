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

/**
 * Interface describing operations for context-specific, <b>immutable</b>, resource management.
 * <p>
 * It is recommended to construct a {@link ResourceKey} instance when adding/updating resources from the {@link Context}
 * to allow cross-referral by sharing the key or personalization when the resource should be private to a specific
 * service.
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
     * Create a Context with a single resource with the given initial {@code key} and {@code value}.
     *
     * @param key   The key to add to the newly created Context.
     * @param value The value to assign to given key.
     * @param <T>   The type of the initial resource.
     * @return A Context with a single resource.
     */
    static <T> Context with(ResourceKey<T> key, T value) {
        return new SimpleContext(key, value);
    }

    /**
     * Creates an empty Context.
     *
     * @return A Context with no resources assigned to it.
     */
    static Context empty() {
        return EmptyContext.INSTANCE;
    }

    /**
     * Indicates whether a resource has been registered with the given {@code key} in this Context.
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
     * Constructs a copy of {@code this} Context with an additional {@code resource} for given {@code key}.
     *
     * @param key      The key under which to register the {@code resource}.
     * @param resource The resource to register.
     * @param <T>      The type of resource registered under the given {@code key}.
     * @return A copy of {@code this} Context with the added given {@code resources} under the given {@code key} to the
     * copy.
     */
    <T> Context withResource(@Nonnull ResourceKey<T> key,
                             @Nonnull T resource);

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
         * Create a new ResourceKey for a resource of type {@code T}. The given {@code debugString} is part of the
         * {@link #toString()} (if not {@code null} or empty) of the created key instance and may be used for debugging
         * purposes.
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
