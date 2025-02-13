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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class Components {

    private final Map<Class<?>, Component<?>> components = new ConcurrentHashMap<>();

    @Nonnull
    public <C> Optional<Component<C>> get(Class<C> type) {
        //noinspection unchecked
        return Optional.ofNullable((Component<C>) components.get(type));
    }

    public <C> Component<C> put(Class<C> type, Component<C> component) {
        //noinspection unchecked
        return (Component<C>) components.put(type, component);
    }

    public Map<Class<?>, Component<?>> all() {
        return Map.copyOf(components);
    }

    public <T> Component<T> computeIfAbsent(Class<T> type,
                                            Function<? super Class<?>, ? extends Component<?>> mappingFunction) {
        //noinspection unchecked
        return (Component<T>) components.computeIfAbsent(type, mappingFunction);
    }
}
