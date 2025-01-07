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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the {@link Context} providing a basic implementation for context-specific resource management.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class SimpleContext implements Context {

    private final Map<ResourceKey<?>, Object> resources;

    /**
     * Constructs a SimpleContext with a single resource.
     *
     * @param <T>   The type of the initial resource
     * @param key   The key of the initial resource
     * @param value The initial resource
     */
    public <T> SimpleContext(ResourceKey<T> key, T value) {
        this(Map.of(key, value));
    }

    private SimpleContext(Map<ResourceKey<?>, Object> resources) {
        this.resources = resources;
    }

    @Override
    public boolean containsResource(@Nonnull ResourceKey<?> key) {
        return resources.containsKey(key);
    }

    @Override
    public <T> T getResource(@Nonnull ResourceKey<T> key) {
        //noinspection unchecked
        return (T) resources.get(key);
    }

    @Override
    public <T> Context withResource(@Nonnull ResourceKey<T> key, @Nonnull T resource) {
        ConcurrentHashMap<ResourceKey<?>, Object> newResources = new ConcurrentHashMap<>(this.resources);
        newResources.put(key, resource);
        return new SimpleContext(newResources);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleContext that = (SimpleContext) o;
        return Objects.equals(resources, that.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(resources);
    }

    @Override
    public String toString() {
        return "SimpleContext{" +
                "resources=" + resources +
                '}';
    }
}
