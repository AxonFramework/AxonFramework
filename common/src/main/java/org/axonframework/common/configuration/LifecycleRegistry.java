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

package org.axonframework.common.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.lifecycle.Phase;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Interface describing the configuration of start and shutdown handlers within Axon's configuration.
 * <p>
 * Allows for configuration of {@link Runnable Runnables}, {@link Supplier suppliers of CompletableFutures},
 * {@link Consumer consumers} of the {@link AxonConfiguration}, and {@link LifecycleHandler LifecycleHandlers}. The
 * invocation order is defined through the (optional) {@code phase} parameter. The
 * {@link Phase} enumeration may be used as a guidance to add operations before/after Axon's
 * regular steps when stating the {@code phase} for the various registration methods.
 *
 * @author Steven van Beelen
 * @see LifecycleHandler
 * @see Phase
 * @since 4.6.0
 */
public interface LifecycleRegistry {

    /**
     * Configures the timeout of each lifecycle phase. The {@code LifecycleRegistry} invokes lifecycle phases during
     * start-up and shutdown of an application.
     * <p>
     * Note that if a lifecycle phase exceeds the configured {@code timeout} and {@code timeUnit} combination, the
     * {@code LifecycleRegistry} will proceed with the following phase. A phase-skip is marked with a warn logging
     * message, as the chances are high this causes undesired side effects.
     * <p>
     * The default lifecycle phase timeout is <b>five</b> seconds.
     *
     * @param timeout  The amount of time to wait for lifecycle phase completion.
     * @param timeUnit The unit in which the {@code timeout} is expressed.
     * @return The current instance of the {@code LifecycleRegistry}, for chaining purposes.
     * @see Phase
     * @see LifecycleHandler
     */
    LifecycleRegistry registerLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit);

    /**
     * Registers a {@code startHandler} to be executed in the default phase {@code 0} when the configuration this
     * registry belongs to is started.
     * <p>
     * Handlers cannot be registered when the configuration has already been created from this registry.
     *
     * @param startHandler The handler to execute when the {@link AxonConfiguration} is started.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#start()
     */
    default LifecycleRegistry onStart(@Nonnull Runnable startHandler) {
        return onStart(0, startHandler);
    }

    /**
     * Registers a {@code startHandler} to be executed in the given {@code phase} when the configuration this registry
     * belongs to is started.
     * <p>
     * Handlers cannot be registered when the configuration has already been created from this registry.
     *
     * @param phase        Defines a {@code phase} in which the start handler will be invoked during
     *                     {@link AxonConfiguration#start()}. When starting the configuration the given handlers are
     *                     started in ascending order based on their {@code phase}.
     * @param startHandler The handler to execute when the {@link AxonConfiguration} is started.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#start()
     */
    default LifecycleRegistry onStart(int phase, @Nonnull Runnable startHandler) {
        requireNonNull(startHandler, "The start handler must not be null.");
        return onStart(phase, (Consumer<Configuration>) configuration -> startHandler.run());
    }

    /**
     * Registers a {@code startHandler} to be executed in the given {@code phase} when the configuration this registry
     * belongs to is started.
     * <p>
     * Handlers cannot be registered when the configuration has already been created from this registry.
     *
     * @param phase        Defines a {@code phase} in which the start handler will be invoked during
     *                     {@link AxonConfiguration#start()}. When starting the configuration the given handlers are
     *                     started in ascending order based on their {@code phase}.
     * @param startHandler The handler to execute when the {@link AxonConfiguration} is started.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#start()
     */
    default LifecycleRegistry onStart(int phase, @Nonnull Supplier<CompletableFuture<?>> startHandler) {
        requireNonNull(startHandler, "The start handler must not be null.");
        return onStart(phase, (LifecycleHandler) configuration -> startHandler.get());
    }

    /**
     * Registers a {@code startHandler} to be executed in the given {@code phase} when the configuration this registry
     * belongs to is started.
     * <p>
     * Handlers cannot be registered when the configuration has already been created from this registry.
     *
     * @param phase        Defines a {@code phase} in which the start handler will be invoked during
     *                     {@link AxonConfiguration#start()}. When starting the configuration the given handlers are
     *                     started in ascending order based on their {@code phase}.
     * @param startHandler The handler to execute when the {@link AxonConfiguration} is started.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#start()
     */
    default LifecycleRegistry onStart(int phase, @Nonnull Consumer<Configuration> startHandler) {
        requireNonNull(startHandler, "startHandler must not be null");
        return onStart(phase, configuration -> {
            try {
                startHandler.accept(configuration);
                return FutureUtils.emptyCompletedFuture();
            } catch (Exception e) {
                CompletableFuture<?> exceptionResult = new CompletableFuture<>();
                exceptionResult.completeExceptionally(e);
                return exceptionResult;
            }
        });
    }

    /**
     * Registers an asynchronous {@code startHandler} to be executed in the given {@code phase} when the configuration
     * this registry belongs to is started.
     * <p>
     * Handlers cannot be registered when the configuration has already been created from this registry.
     *
     * @param phase        Defines a {@code phase} in which the start handler will be invoked during
     *                     {@link AxonConfiguration#start()}. When starting the configuration the given handlers are
     *                     started in ascending order based on their {@code phase}.
     * @param startHandler The handler to be executed asynchronously when the {@link AxonConfiguration} is started.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#start()
     */
    LifecycleRegistry onStart(int phase, @Nonnull LifecycleHandler startHandler);

    /**
     * Registers a {@code shutdownHandler} to be executed in the default phase {@code 0} when the configuration this
     * registry belongs to is shut down.
     * <p>
     * Handlers cannot be registered when the configuration has already been created from this registry.
     *
     * @param shutdownHandler The handler to execute when the {@link AxonConfiguration} is shut down.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#shutdown()
     */
    default LifecycleRegistry onShutdown(@Nonnull Runnable shutdownHandler) {
        return onShutdown(0, shutdownHandler);
    }

    /**
     * Registers a {@code shutdownHandler} to be executed in the given {@code phase} when the configuration this
     * registry belongs to is shut down.
     * <p>
     * Handlers cannot be registered when the configuration has already been created from this registry.
     *
     * @param phase           Defines a phase in which the shutdown handler will be invoked during
     *                        {@link AxonConfiguration#shutdown()}. When shutting down the configuration the given
     *                        handlers are executing in descending order based on their {@code phase}.
     * @param shutdownHandler The handler to execute when the {@link AxonConfiguration} is shut down.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#shutdown()
     */
    default LifecycleRegistry onShutdown(int phase, @Nonnull Runnable shutdownHandler) {
        requireNonNull(shutdownHandler, "The shutdown handler must not be null.");
        return onShutdown(phase, (Consumer<Configuration>) configuration -> shutdownHandler.run());
    }

    /**
     * Registers a {@code shutdownHandler} to be executed in the given {@code phase} when the configuration this
     * registry belongs to is shut down.
     * <p>
     * The behavior for handlers that are registered when the configuration is already shut down is undefined.
     *
     * @param phase           Defines a phase in which the shutdown handler will be invoked during
     *                        {@link AxonConfiguration#shutdown()}. When shutting down the configuration the given
     *                        handlers are executing in descending order based on their {@code phase}.
     * @param shutdownHandler The handler to execute when the {@link AxonConfiguration} is shut down.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#shutdown()
     */
    default LifecycleRegistry onShutdown(int phase, @Nonnull Supplier<CompletableFuture<?>> shutdownHandler) {
        requireNonNull(shutdownHandler, "The shutdown handler must not be null.");
        return onShutdown(phase, (LifecycleHandler) configuration -> shutdownHandler.get());
    }

    /**
     * Registers a {@code shutdownHandler} to be executed in the given {@code phase} when the configuration this
     * registry belongs to is shut down.
     * <p>
     * The behavior for handlers that are registered when the configuration is already shut down is undefined.
     *
     * @param phase           Defines a phase in which the shutdown handler will be invoked during
     *                        {@link AxonConfiguration#shutdown()}. When shutting down the configuration the given
     *                        handlers are executing in descending order based on their {@code phase}.
     * @param shutdownHandler The handler to execute when the {@link AxonConfiguration} is shut down.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#shutdown()
     */
    default LifecycleRegistry onShutdown(int phase, @Nonnull Consumer<Configuration> shutdownHandler) {
        requireNonNull(shutdownHandler, "shutdownHandler must not be null");
        return onShutdown(phase, configuration -> {
            try {
                shutdownHandler.accept(configuration);
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
     * configuration this registry belongs to is shut down.
     * <p>
     * The behavior for handlers that are registered when the configuration is already shut down is undefined.
     *
     * @param phase           Defines a phase in which the shutdown handler will be invoked during
     *                        {@link AxonConfiguration#shutdown()}. When shutting down the configuration the given
     *                        handlers are executing in descending order based on their {@code phase}.
     * @param shutdownHandler The handler to be executed asynchronously when the {@link AxonConfiguration} is shut
     *                        down.
     * @return The current instance of the {@code LifecycleRegistry} for a fluent API.
     * @see AxonConfiguration#shutdown()
     */
    LifecycleRegistry onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler);
}
