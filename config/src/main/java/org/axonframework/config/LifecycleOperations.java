/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.config;

import org.axonframework.common.FutureUtils;

import java.util.concurrent.CompletableFuture;

/**
 * Interface describing the configuration of start and shutdown handlers within Axon's configuration.
 * <p>
 * Both {@link Runnable Runnables} or {@link LifecycleHandler LifecycleHandlers} can be configured. The invocation order
 * is defined through the (optional) {@code phase} parameter. The {@link org.axonframework.lifecycle.Phase} enumeration
 * may be used as a guidance to add operations before/after Axon's regular steps.
 *
 * @author Steven van Beelen
 * @see LifecycleHandler
 * @see org.axonframework.lifecycle.Phase
 * @since 4.6.0
 */
public interface LifecycleOperations {

    /**
     * Registers a {@code startHandler} to be executed in the default phase {@code 0} when this Configuration is
     * started.
     * <p>
     * The behavior for handlers that are registered when the Configuration is already started is undefined.
     *
     * @param startHandler the handler to execute when the configuration is started
     * @see Configuration#start()
     */
    default void onStart(Runnable startHandler) {
        onStart(0, startHandler);
    }

    /**
     * Registers a {@code startHandler} to be executed in the given {@code phase} when this Configuration is started.
     * <p>
     * The behavior for handlers that are registered when the Configuration is already started is undefined.
     *
     * @param phase        defines a {@code phase} in which the start handler will be invoked during {@link
     *                     Configuration#start()}. When starting the configuration the given handlers are started in
     *                     ascending order based on their {@code phase}
     * @param startHandler the handler to execute when the configuration is started
     * @see Configuration#start()
     */
    default void onStart(int phase, Runnable startHandler) {
        onStart(phase, () -> {
            try {
                startHandler.run();
                return FutureUtils.emptyCompletedFuture();
            } catch (Exception e) {
                CompletableFuture<?> exceptionResult = new CompletableFuture<>();
                exceptionResult.completeExceptionally(e);
                return exceptionResult;
            }
        });
    }

    /**
     * Registers an asynchronous {@code startHandler} to be executed in the given {@code phase} when this Configuration
     * is started.
     * <p>
     * The behavior for handlers that are registered when the Configuration is already started is undefined.
     *
     * @param phase        defines a {@code phase} in which the start handler will be invoked during {@link
     *                     Configuration#start()}. When starting the configuration the given handlers are started in
     *                     ascending order based on their {@code phase}
     * @param startHandler the handler to be executed asynchronously when the configuration is started
     * @see Configuration#start()
     */
    void onStart(int phase, LifecycleHandler startHandler);

    /**
     * Registers a {@code shutdownHandler} to be executed in the default phase {@code 0} when the Configuration is shut
     * down.
     * <p>
     * The behavior for handlers that are registered when the Configuration is already shut down is undefined.
     *
     * @param shutdownHandler the handler to execute when the Configuration is shut down
     * @see Configuration#shutdown()
     */
    default void onShutdown(Runnable shutdownHandler) {
        onShutdown(0, shutdownHandler);
    }

    /**
     * Registers a {@code shutdownHandler} to be executed in the given {@code phase} when the Configuration is shut
     * down.
     * <p>
     * The behavior for handlers that are registered when the Configuration is already shut down is undefined.
     *
     * @param phase           defines a phase in which the shutdown handler will be invoked during {@link
     *                        Configuration#shutdown()}. When shutting down the configuration the given handlers are
     *                        executing in descending order based on their {@code phase}
     * @param shutdownHandler the handler to execute when the Configuration is shut down
     * @see Configuration#shutdown()
     */
    default void onShutdown(int phase, Runnable shutdownHandler) {
        onShutdown(phase, () -> {
            try {
                shutdownHandler.run();
                return FutureUtils.emptyCompletedFuture();
            } catch (Exception e) {
                CompletableFuture<?> exceptionResult = new CompletableFuture<>();
                exceptionResult.completeExceptionally(e);
                return exceptionResult;
            }
        });
    }

    /**
     * Registers an asynchronous {@code shutdownHandler} to be executed in the given {@code phase} when the
     * Configuration is shut down.
     * <p>
     * The behavior for handlers that are registered when the Configuration is already shut down is undefined.
     *
     * @param phase           defines a phase in which the shutdown handler will be invoked during {@link
     *                        Configuration#shutdown()}. When shutting down the configuration the given handlers are
     *                        executing in descending order based on their {@code phase}
     * @param shutdownHandler the handler to be executed asynchronously when the Configuration is shut down
     * @see Configuration#shutdown()
     */
    void onShutdown(int phase, LifecycleHandler shutdownHandler);
}
