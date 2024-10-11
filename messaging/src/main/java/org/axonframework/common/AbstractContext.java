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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstract implementation of the {@link Context} providing a sane implementation for context-specific resource
 * management.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public abstract class AbstractContext implements Context {

    private final ConcurrentMap<ResourceKey<?>, Object> resources = new ConcurrentHashMap<>();

    @Override
    public boolean containsResource(ResourceKey<?> key) {
        return resources.containsKey(key);
    }

    @Override
    public <T> T getResource(ResourceKey<T> key) {
        //noinspection unchecked
        return (T) resources.get(key);
    }

    @Override
    public <T> T putResource(ResourceKey<T> key, T resource) {
        //noinspection unchecked
        return (T) resources.put(key, resource);
    }

    @Override
    public <T> T updateResource(ResourceKey<T> key, Function<T, T> resourceUpdater) {
        //noinspection unchecked
        return (T) resources.compute(key, (k, v) -> resourceUpdater.apply((T) v));
    }

    @Override
    public <T> T putResourceIfAbsent(ResourceKey<T> key, T resource) {
        //noinspection unchecked
        return (T) resources.putIfAbsent(key, resource);
    }

    @Override
    public <T> T computeResourceIfAbsent(ResourceKey<T> key, Supplier<T> resourceSupplier) {
        //noinspection unchecked
        return (T) resources.computeIfAbsent(key, t -> resourceSupplier.get());
    }

    @Override
    public <T> T removeResource(ResourceKey<T> key) {
        //noinspection unchecked
        return (T) resources.remove(key);
    }

    @Override
    public <T> boolean removeResource(ResourceKey<T> key, T expectedResource) {
        return resources.remove(key, expectedResource);
    }
}
