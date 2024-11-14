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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;

import java.util.Objects;
import java.util.UUID;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;

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
     * Constructs a copy of {@code this} {@link Context} adding the given {@code resources} under the given {@code key}
     * to the copy.
     *
     * @param key      The key under which to register the {@code resource}.
     * @param resource The resource to register.
     * @param <T>      The type of resource registered under the given {@code key}.
     * @return A copy of {@code this} {@link Context} with the added given {@code resources} under the given {@code key}
     * to the copy.
     */
    <T> Context withResource(@Nonnull ResourceKey<T> key,
                             @Nonnull T resource);

    /**
     * Object that is used as a key to retrieve and register resources of a given type in a {@link Context}.
     * <p>
     * Instance of a {@code ResourceKey} can be created using either {@link #uniqueKey()}, {@link #sharedKey(Class)}, or
     * {@link #sharedKey(String)}. The former option will construct a unique key at all times, while the {@link Class} and
     * {@link String} based factory methods result in an identical {@code ResourceKey} if the {@code Class} or
     * {@code String} is reused.
     *
     * @param <T> The type of resource registered under this key.
     * @author Allard Buijze
     * @author Mitchell Herrijgers
     * @author Steven van Beelen
     * @since 5.0.0
     */
    @SuppressWarnings("unused") // Suppresses the warning that the generic type is not used.
    final class ResourceKey<T> {

        private final Object identity;
        private final String debugString;

        private ResourceKey(@Nonnull Object identity,
                            @Nonnull String debugString) {
            this.identity = identity;
            this.debugString = debugString;
        }

        /**
         * Creates a {@link ResourceKey} using a {@link UUID#randomUUID() random UUID} as the key's identity.
         *
         * @param <T> The type of resource of this key.
         * @return A {@link ResourceKey} using a {@link UUID#randomUUID() random UUID} as the key's identity, used for
         * adding and retrieving context-specific resources.
         */
        public static <T> ResourceKey<T> uniqueKey() {
            return sharedKey(UUID.randomUUID().toString());
        }

        /**
         * Creates a {@link ResourceKey} using the given {@code clazz} as the key's identity.
         * <p>
         * Creating another {@code ResourceKey} with the same {@link Class} results in an identical
         * {@code ResourceKey}.
         *
         * @param clazz The {@link Class} used as the {@link ResourceKey resource key's} identity.
         * @param <T>   The type of resource of this key.
         * @return A {@link ResourceKey} using the given {@code clazz} as the key's identity, used for adding and
         * retrieving context-specific resources.
         */
        public static <T> ResourceKey<T> sharedKey(@Nonnull Class<T> clazz) {
            return sharedKey(clazz.getName());
        }

        /**
         * Creates a {@link ResourceKey} using the given {@code identity} as the key's identity.
         * <p>
         * Creating another {@code ResourceKey} with the same {@code identity} results in an identical
         * {@code ResourceKey}.
         *
         * @param identity The {@link String} defining the identity of this {@link ResourceKey}.
         * @param <T>      The type of resource of this key.
         * @return A {@link ResourceKey} using the given {@code identity} as the key's identity, used for adding and
         * retrieving context-specific resources.
         */
        public static <T> ResourceKey<T> sharedKey(@Nonnull String identity) {
            return new ResourceKey<>(identity, identity);
        }

        /**
         * Creates a copy of {@code this} {@link ResourceKey} with the given {@code debugString} used as this key's
         * {@link #toString()} output.
         * <p>
         * When no {@code debugString} is consciously given, the identity of the {@code ResourceKey} equals as the debug
         * string.
         *
         * @param debugString A {@link String} to recognize this key during debugging.
         * @return A copy of {@code this} {@link ResourceKey} with the given {@code debugString} used as this key's
         * {@link #toString()} output, used for adding and retrieving context-specific resources.
         */
        public ResourceKey<T> withDebugString(@Nonnull String debugString) {
            assertNonEmpty(debugString, "The debug string cannot be null or empty.");
            return new ResourceKey<>(this.identity, debugString);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ResourceKey<?> that = (ResourceKey<?>) o;
            return Objects.equals(identity, that.identity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identity);
        }

        @Override
        public String toString() {
            return debugString;
        }
    }
}
