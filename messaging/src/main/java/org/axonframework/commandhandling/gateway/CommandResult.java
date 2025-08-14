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

package org.axonframework.commandhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.serialization.ConversionException;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Interface describing the result from command handling.
 *
 * @author Allard Buijze
 * @since 0.6.0
 */
public interface CommandResult {

    /**
     * Returns the result {@link Message} within a {@link CompletableFuture} of command handling.
     *
     * @return The result {@link Message} within a {@code CompletableFuture} of command handling.
     */
    CompletableFuture<? extends Message<?>> getResultMessage();

    /**
     * Returns the result of command handling, cast to the given {@code type}, within a {@link CompletableFuture}.
     *
     * @param type The expected result type of command handling.
     * @param <R>  The type of the command result.
     * @return The result of command handling, cast to the given {@code type}, within a {@code CompletableFuture}.
     */
    default <R> CompletableFuture<R> resultAs(@Nonnull Class<R> type) {
        requireNonNull(type, "The result type must not be null");
        return getResultMessage().thenApply(r -> {
            if (r == null || r.payload() == null) {
                return null;
            }
            if (type.isInstance(r.payload())) {
                return type.cast(r.payload());
            }
            throw new ConversionException(
                    String.format("Expected result of type [%s] in the CommandResult, but got [%s]",
                                  type.getName(),
                                  r.payload().getClass().getName())
            );
        });
    }

    /**
     * Attaches the given {@code successHandler} to {@code this} command result.
     * <p>
     * The {@code successHandler} is invoked when command handling resolves successfully.
     *
     * @param successHandler A consumer of the command result {@code Message}, to be invoked upon successful command
     *                       handling.
     * @return This command result, invoking the given {@code successHandler} when command handling resolves
     * successfully.
     */
    default CommandResult onSuccess(@Nonnull Consumer<Message<?>> successHandler) {
        requireNonNull(successHandler, "The success handler must not be null.");
        getResultMessage().whenComplete((r, e) -> {
            if (e == null) {
                successHandler.accept(r);
            }
        });
        return this;
    }

    /**
     * Attaches the given {@code successHandler} to {@code this} command result, expecting the given
     * {@code resultType}.
     * <p>
     * The {@code successHandler} is invoked when command handling resolves successfully.
     *
     * @param resultType     The expected result type of command handling.
     * @param successHandler A bi-consumer of the command result {@code Message}, to be invoked upon successful command
     *                       handling.
     * @param <R>            The type of the command result.
     * @return This command result, invoking the given {@code successHandler} when command handling resolves
     * successfully.
     */
    default <R> CommandResult onSuccess(@Nonnull Class<R> resultType,
                                        @Nonnull BiConsumer<R, Message<?>> successHandler) {
        requireNonNull(resultType, "The result type must not be null.");
        requireNonNull(successHandler, "The success handler must not be null.");
        getResultMessage().whenComplete((r, e) -> {
            if (e == null) {
                successHandler.accept(resultType.cast(r.payload()), r);
            }
        });
        return this;
    }

    /**
     * Attaches the given {@code successHandler} to {@code this} command result, expecting the given
     * {@code resultType}.
     * <p>
     * The {@code successHandler} is invoked when command handling resolves successfully.
     *
     * @param resultType     The expected result type of command handling.
     * @param successHandler A consumer of the command result {@code Message}, to be invoked upon successful command
     *                       handling.
     * @param <R>            The type of the command result.
     * @return This command result, invoking the given {@code successHandler} when command handling resolves
     * successfully.
     */
    default <R> CommandResult onSuccess(@Nonnull Class<R> resultType,
                                        @Nonnull Consumer<R> successHandler) {
        requireNonNull(successHandler, "The success handler must not be null.");
        return onSuccess(resultType, (result, message) -> successHandler.accept(result));
    }

    /**
     * Attaches the given {@code errorHandler} to {@code this} command result.
     * <p>
     * The {@code errorHandler} is invoked when command handling fails.
     *
     * @param errorHandler A consumer of the {@link Throwable} that may follow when command handling fails.
     * @return This command result, invoking the given {@code errorHandler} when command handling resolves with an
     * error.
     */
    default CommandResult onError(@Nonnull Consumer<Throwable> errorHandler) {
        requireNonNull(errorHandler, "The error handler must not be null.");
        getResultMessage().whenComplete((r, e) -> {
            if (e != null) {
                errorHandler.accept(e);
            }
        });
        return this;
    }
}
