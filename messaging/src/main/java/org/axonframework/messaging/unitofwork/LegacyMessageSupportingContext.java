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

package org.axonframework.messaging.unitofwork;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.Message;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Temporary implementation of the {@link ProcessingContext} that should eventually disappear.
 * Can only be constructed with a {@link org.axonframework.messaging.Message} as
 * parameter that serves as his only resource. Any operation except getting the message
 * from the context will fail.
 * <p>
 * Especially created for "sync" components during the AF5 lifecycle.
 * Therefore has been introduced as immediately deprecated.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 * @deprecated Only in use for legacy "sync" components
 */
@Deprecated
public class LegacyMessageSupportingContext implements ProcessingContext {

    private static final String UNSUPPORTED_MESSAGE = "Cannot register lifecycle actions in this ProcessingContext";
    private final Message<?> message;

    public LegacyMessageSupportingContext(Message<?> message) {
        this.message = message;
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
    public ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public ProcessingLifecycle onError(ErrorHandler action) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public boolean containsResource(@Nonnull ResourceKey<?> key) {
        return key.equals(Message.resourceKey);
    }

    @Override
    public <T> T getResource(@Nonnull ResourceKey<T> key) {
        if(key.equals(Message.resourceKey)) {
            //noinspection unchecked
            return (T) message;
        }
        return null;
    }

    @Override
    public <T> ProcessingContext withResource(@Nonnull ResourceKey<T> key,
                                              @Nonnull T resource) {
        return new ResourceOverridingProcessingContext<>(this, key, resource);
    }

    @Override
    public <T> T putResource(@Nonnull ResourceKey<T> key,
                             @Nonnull T resource) {
        throw new IllegalArgumentException("Cannot put resources in this ProcessingContext");
    }

    @Override
    public <T> T updateResource(@Nonnull ResourceKey<T> key,
                                @Nonnull UnaryOperator<T> resourceUpdater) {
        throw new IllegalArgumentException("Cannot update resources in this ProcessingContext");
    }

    @Override
    public <T> T putResourceIfAbsent(@Nonnull ResourceKey<T> key,
                                     @Nonnull T resource) {
        throw new IllegalArgumentException("Cannot put resources in this ProcessingContext");
    }

    @Override
    public <T> T computeResourceIfAbsent(@Nonnull ResourceKey<T> key,
                                         @Nonnull Supplier<T> resourceSupplier) {
        throw new IllegalArgumentException("Cannot compute resources in this ProcessingContext");
    }

    @Override
    public <T> T removeResource(@Nonnull ResourceKey<T> key) {
        return null;
    }

    @Override
    public <T> boolean removeResource(@Nonnull ResourceKey<T> key,
                                      @Nullable T expectedResource) {
        return expectedResource == null;
    }
}
