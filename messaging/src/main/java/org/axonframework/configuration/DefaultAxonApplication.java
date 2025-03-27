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
import org.axonframework.common.FutureUtils;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.Assert.assertStrictPositive;

/**
 * Default implementation of the {@code AxonApplication}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class DefaultAxonApplication implements ApplicationConfigurer, LifecycleRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final Runnable NOTHING = () -> {
    };

    private final TreeMap<Integer, List<LifecycleHandler>> startHandlers = new TreeMap<>();
    private final TreeMap<Integer, List<LifecycleHandler>> shutdownHandlers = new TreeMap<>(Comparator.reverseOrder());
    private final DefaultComponentRegistry componentRegistry;
    private long lifecyclePhaseTimeout = 5;
    private TimeUnit lifecyclePhaseTimeunit = TimeUnit.SECONDS;
    private boolean enhancerScanning = true;

    private final AtomicReference<AxonConfiguration> configuration = new AtomicReference<>();

    public DefaultAxonApplication() {
        this.componentRegistry = new DefaultComponentRegistry();
    }

    @Override
    public DefaultAxonApplication onStart(int phase, @Nonnull LifecycleHandler startHandler) {
        return registerLifecycleHandler(startHandlers, phase, startHandler);
    }

    @Override
    public DefaultAxonApplication onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
        return registerLifecycleHandler(shutdownHandlers, phase, shutdownHandler);
    }

    private DefaultAxonApplication registerLifecycleHandler(Map<Integer, List<LifecycleHandler>> lifecycleHandlers,
                                          int phase,
                                          @Nonnull LifecycleHandler lifecycleHandler) {
        if (configuration.get() != null) {
            throw new IllegalArgumentException(
                    "Cannot register lifecycle handlers when the configuration is already initialized");
        }
        lifecycleHandlers.compute(phase, (p, handlers) -> {
            if (handlers == null) {
                handlers = new CopyOnWriteArrayList<>();
            }
            handlers.add(requireNonNull(lifecycleHandler, "Cannot register null lifecycle handlers."));
            return handlers;
        });
        return this;
    }

    @Override
    public DefaultAxonApplication registerLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit) {
        assertStrictPositive(timeout, "The lifecycle phase timeout should be strictly positive");
        requireNonNull(timeUnit, "The lifecycle phase time unit should not be null");
        this.lifecyclePhaseTimeout = timeout;
        this.lifecyclePhaseTimeunit = timeUnit;
        return this;
    }

    // TODO - Move to componentRegistry
    public DefaultAxonApplication disableEnhancerScanning() {
        this.enhancerScanning = false;
        return this;
    }

    @Override
    public synchronized AxonConfiguration build() {
        if (configuration.get() == null) {
            if (enhancerScanning) {
                scanForConfigurationEnhancers();
            }
            configuration.set(new AxonConfigurationImpl(componentRegistry.build(this)));
        }
        return configuration.get();
    }

    private void scanForConfigurationEnhancers() {
        ServiceLoader<ConfigurationEnhancer> enhancerLoader =
                ServiceLoader.load(ConfigurationEnhancer.class, getClass().getClassLoader());
        List<ConfigurationEnhancer> enhancers = new ArrayList<>();
        enhancerLoader.forEach(enhancers::add);
        enhancers.forEach(componentRegistry::registerEnhancer);
    }

    @Override
    public ApplicationConfigurer componentRegistry(Consumer<ComponentRegistry> action) {
        action.accept(componentRegistry);
        return this;
    }

    @Override
    public ApplicationConfigurer lifecycleRegistry(Consumer<LifecycleRegistry> lifecycleRegistrar) {
        lifecycleRegistrar.accept(this);
        return this;
    }

    private class AxonConfigurationImpl implements AxonConfiguration {

        private final NewConfiguration config;
        private final AtomicReference<LifecycleState> lifecycleState = new AtomicReference<>(LifecycleState.DOWN);

        private AxonConfigurationImpl(NewConfiguration config) {
            this.config = config;
        }

        @Override
        public void start() {
            if (lifecycleState.compareAndSet(LifecycleState.DOWN, LifecycleState.STARTING_UP)) {
                logger.debug("Initiating start up");
                verifyIdentifierFactory();
                invokeStartHandlers();
                lifecycleState.set(LifecycleState.UP);
                logger.debug("Finalized start sequence");
            }
        }

        private void invokeStartHandlers() {
            invokeLifecycleHandlers(
                    startHandlers,
                    (phase, e) -> {
                        logger.debug("Start up is being ended prematurely due to an exception");
                        String startFailure = String.format(
                                "One of the start handlers in phase [%d] failed with the following exception: ",
                                phase
                        );
                        logger.warn(startFailure, e);

                        invokeShutdownHandlers();
                        throw new LifecycleHandlerInvocationException(startFailure, e);
                    }
            );
        }

        /**
         * Verifies that a valid {@link IdentifierFactory} class has been configured.
         *
         * @throws IllegalArgumentException if the configured factory is not valid.
         */
        private void verifyIdentifierFactory() {
            try {
                //noinspection ResultOfMethodCallIgnored
                IdentifierFactory.getInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException("The configured IdentifierFactory could not be instantiated.", e);
            }
        }

        @Override
        public void shutdown() {
            if (lifecycleState.compareAndSet(LifecycleState.UP, LifecycleState.SHUTTING_DOWN)) {
                logger.debug("Initiating shutdown");
                invokeShutdownHandlers();

                lifecycleState.set(LifecycleState.DOWN);
                logger.debug("Finalized shutdown sequence");
            }
        }

        /**
         * Invokes all registered shutdown handlers.
         */
        protected void invokeShutdownHandlers() {
            invokeLifecycleHandlers(
                    shutdownHandlers,
                    (phase, e) -> logger.warn(
                            "One of the shutdown handlers in phase [{}] failed with the following exception: ",
                            phase, e
                    )
            );
        }

        private void invokeLifecycleHandlers(TreeMap<Integer, List<LifecycleHandler>> lifecycleHandlerMap,
                                             BiConsumer<Integer, Exception> exceptionHandler) {
            Integer currentLifecyclePhase;
            Map.Entry<Integer, List<LifecycleHandler>> phasedHandlers = lifecycleHandlerMap.firstEntry();
            if (phasedHandlers == null) {
                return;
            }

            do {
                currentLifecyclePhase = phasedHandlers.getKey();
                logger.debug("Entered {} handler lifecycle phase [{}]",
                             lifecycleState.get().description,
                             currentLifecyclePhase);

                List<LifecycleHandler> handlers = phasedHandlers.getValue();
                try {
                    handlers.stream()
                            .map(lch -> lch.run(this))
                            .map(c -> c.thenRun(NOTHING))
                            .reduce(CompletableFuture::allOf)
                            .orElse(FutureUtils.emptyCompletedFuture())
                            .get(lifecyclePhaseTimeout, lifecyclePhaseTimeunit);
                } catch (CompletionException | ExecutionException e) {
                    exceptionHandler.accept(currentLifecyclePhase, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn(
                            "Completion interrupted during {} phase [{}]. Proceeding to following phase",
                            lifecycleState.get().description, currentLifecyclePhase);
                } catch (TimeoutException e) {
                    final long lifecyclePhaseTimeoutInMillis = TimeUnit.MILLISECONDS.convert(lifecyclePhaseTimeout,
                                                                                             lifecyclePhaseTimeunit);
                    logger.warn(
                            "Timed out during {} phase [{}] after {}ms. Proceeding to following phase",
                            lifecycleState.get().description, currentLifecyclePhase, lifecyclePhaseTimeoutInMillis);
                }
            } while ((phasedHandlers = lifecycleHandlerMap.higherEntry(currentLifecyclePhase)) != null);
        }

        @Override
        public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type, @Nonnull String name) {
            return config.getOptionalComponent(type, name);
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type, @Nonnull String name, @Nonnull Supplier<C> defaultImpl) {
            return config.getComponent(type, name, defaultImpl);
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type) {
            return config.getComponent(type);
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type, @Nonnull Supplier<C> defaultImpl) {
            return config.getComponent(type, defaultImpl);
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type, @Nonnull String name) {
            return config.getComponent(type, name);
        }

        @Override
        public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type) {
            return config.getOptionalComponent(type);
        }

        @Override
        public List<NewConfiguration> getModuleConfigurations() {
            return config.getModuleConfigurations();
        }

        @Override
        public Optional<NewConfiguration> getModuleConfiguration(String name) {
            return config.getModuleConfiguration(name);
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("components", componentRegistry);
            descriptor.describeProperty("lifecycleState", lifecycleState.get());
        }
    }

    private enum LifecycleState {

        DOWN("down"),
        STARTING_UP("start"),
        UP("up"),
        SHUTTING_DOWN("shutdown");

        private final String description;

        LifecycleState(String description) {
            this.description = description;
        }
    }
}
