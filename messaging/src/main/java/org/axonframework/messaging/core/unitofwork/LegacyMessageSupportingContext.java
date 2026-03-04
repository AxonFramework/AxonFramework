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

package org.axonframework.messaging.core.unitofwork;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.axonframework.common.configuration.ComponentNotFoundException;
import org.axonframework.messaging.core.Message;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Legacy implementation of the {@link ProcessingContext} that should only be used for legacy components. Can only be
 * constructed with a {@link org.axonframework.messaging.core.Message} as parameter that is added to the resources. Any
 * lifecycle operation will fail.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 * @deprecated Only in use for legacy "sync" components.
 */
@Deprecated
public class LegacyMessageSupportingContext implements ProcessingContext {

    private static final String UNSUPPORTED_MESSAGE = "Cannot register lifecycle actions in this ProcessingContext";
    private final ConcurrentMap<ResourceKey<?>, Object> resources;

    /**
     * Initialize the {@link ProcessingContext} with the given {@code message} as the only resource.
     *
     * @param message The message to be used as the only resource in this context.
     */
    public LegacyMessageSupportingContext(@NonNull Message message) {
        this.resources = new ConcurrentHashMap<>();
        Message.addToContext(this, message);
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    @Override
    public boolean isError() {
        return false;
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public boolean isCompleted() {
        return false;
    }

    @Override
    public ProcessingLifecycle on(@NonNull Phase phase, @NonNull Function<ProcessingContext, CompletableFuture<?>> action) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public ProcessingLifecycle onError(@NonNull ErrorHandler action) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public ProcessingLifecycle whenComplete(@NonNull Consumer<ProcessingContext> action) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public boolean containsResource(@NonNull ResourceKey<?> key) {
        return resources.containsKey(key);
    }

    @Override
    public <T> T getResource(@NonNull ResourceKey<T> key) {
        //noinspection unchecked
        return (T) resources.get(key);
    }

    @Override
    public Map<ResourceKey<?>, Object> resources() {
        return Map.copyOf(resources);
    }

    @Override
    public <T> T putResource(@NonNull ResourceKey<T> key,
                             @NonNull T resource) {
        //noinspection unchecked
        return (T) resources.put(key, resource);
    }

    @Override
    public <T> T updateResource(@NonNull ResourceKey<T> key,
                                @NonNull UnaryOperator<T> resourceUpdater) {
        //noinspection unchecked
        return (T) resources.compute(key, (k, v) -> resourceUpdater.apply((T) v));
    }

    @Override
    public <T> T putResourceIfAbsent(@NonNull ResourceKey<T> key,
                                     @NonNull T resource) {
        //noinspection unchecked
        return (T) resources.putIfAbsent(key, resource);
    }

    @Override
    public <T> T computeResourceIfAbsent(@NonNull ResourceKey<T> key,
                                         @NonNull Supplier<T> resourceSupplier) {
        //noinspection unchecked
        return (T) resources.computeIfAbsent(key, t -> resourceSupplier.get());
    }

    @Override
    public <T> T removeResource(@NonNull ResourceKey<T> key) {
        //noinspection unchecked
        return (T) resources.remove(key);
    }

    @Override
    public <T> boolean removeResource(@NonNull ResourceKey<T> key,
                                      @NonNull T expectedResource) {
        return resources.remove(key, expectedResource);
    }

    @NonNull
    @Override
    public <C> C component(@NonNull Class<C> type, @Nullable String name) {
        throw new ComponentNotFoundException(type, name);
    }
}
