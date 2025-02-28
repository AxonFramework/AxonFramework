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
import org.axonframework.configuration.Component.Identifier;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * Default implementation of the {@code RootConfigurer}.
 * <p>
 * Note that this Configurer implementation is not thread-safe.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class DefaultRootConfigurer extends AbstractConfigurer<RootConfigurer> implements RootConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final Runnable NOTHING = () -> {
    };

    private final TreeMap<Integer, List<LifecycleHandler>> startHandlers = new TreeMap<>();
    private final TreeMap<Integer, List<LifecycleHandler>> shutdownHandlers = new TreeMap<>(Comparator.reverseOrder());
    private long lifecyclePhaseTimeout = 5;
    private TimeUnit lifecyclePhaseTimeunit = TimeUnit.SECONDS;

    private final RootConfigurationImpl rootConfig = new RootConfigurationImpl();

    /**
     * Initialize the {@code RootConfigurer} with a {@code null} {@link LifecycleSupportingConfiguration}.
     */
    protected DefaultRootConfigurer() {
        super(null); // 🦸 <- Is this a train? Is this a bullet? No, it is Super Null!
    }

    @Override
    public void onStart(int phase, @Nonnull LifecycleHandler startHandler) {
        registerLifecycleHandler(startHandlers, phase, startHandler);
    }

    @Override
    public void onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
        registerLifecycleHandler(shutdownHandlers, phase, shutdownHandler);
    }

    private void registerLifecycleHandler(Map<Integer, List<LifecycleHandler>> lifecycleHandlers,
                                          int phase,
                                          @Nonnull LifecycleHandler lifecycleHandler) {
        lifecycleHandlers.compute(phase, (p, handlers) -> {
            if (handlers == null) {
                handlers = new CopyOnWriteArrayList<>();
            }
            handlers.add(requireNonNull(lifecycleHandler, "Cannot register null lifecycle handlers."));
            return handlers;
        });
    }

    @Override
    public RootConfigurer configureLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit) {
        assertStrictPositive(timeout, "The lifecycle phase timeout should be strictly positive");
        requireNonNull(timeUnit, "The lifecycle phase time unit should not be null");
        this.lifecyclePhaseTimeout = timeout;
        this.lifecyclePhaseTimeunit = timeUnit;
        return this;
    }

    @Override
    protected LifecycleSupportingConfiguration config() {
        return rootConfig;
    }

    @SuppressWarnings("unchecked")
    @Override
    public RootConfiguration build() {
        super.build();
        return rootConfig;
    }

    private class RootConfigurationImpl implements RootConfiguration {

        private final AtomicBoolean isRunning;
        private Integer currentLifecyclePhase = null;
        private LifecycleState lifecycleState = LifecycleState.DOWN;

        private RootConfigurationImpl() {
            this.isRunning = new AtomicBoolean(false);
        }

        @Override
        public void start() {
            if (!isRunning.getAndSet(true)) {
                verifyIdentifierFactory();
                invokeStartHandlers();
            }
        }

        private void invokeStartHandlers() {
            logger.debug("Initiating start up");
            lifecycleState = LifecycleState.STARTING_UP;

            invokeLifecycleHandlers(
                    startHandlers,
                    e -> {
                        logger.debug("Start up is being ended prematurely due to an exception");
                        String startFailure = String.format(
                                "One of the start handlers in phase [%d] failed with the following exception: ",
                                currentLifecyclePhase
                        );
                        logger.warn(startFailure, e);

                        invokeShutdownHandlers();
                        throw new LifecycleHandlerInvocationException(startFailure, e);
                    }
            );

            lifecycleState = LifecycleState.UP;
            logger.debug("Finalized start sequence");
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
            if (isRunning.getAndSet(false)) {
                invokeShutdownHandlers();
            }
        }

        /**
         * Invokes all registered shutdown handlers.
         */
        protected void invokeShutdownHandlers() {
            logger.debug("Initiating shutdown");
            lifecycleState = LifecycleState.SHUTTING_DOWN;

            invokeLifecycleHandlers(
                    shutdownHandlers,
                    e -> logger.warn(
                            "One of the shutdown handlers in phase [{}] failed with the following exception: ",
                            currentLifecyclePhase, e
                    )
            );

            lifecycleState = LifecycleState.DOWN;
            logger.debug("Finalized shutdown sequence");
        }

        private void invokeLifecycleHandlers(TreeMap<Integer, List<LifecycleHandler>> lifecycleHandlerMap,
                                             Consumer<Exception> exceptionHandler) {
            Map.Entry<Integer, List<LifecycleHandler>> phasedHandlers = lifecycleHandlerMap.firstEntry();
            if (phasedHandlers == null) {
                return;
            }

            do {
                currentLifecyclePhase = phasedHandlers.getKey();
                logger.debug("Entered {} handler lifecycle phase [{}]",
                             lifecycleState.description,
                             currentLifecyclePhase);

                List<LifecycleHandler> handlers = phasedHandlers.getValue();
                try {
                    handlers.stream()
                            .map(LifecycleHandler::run)
                            .map(c -> c.thenRun(NOTHING))
                            .reduce(CompletableFuture::allOf)
                            .orElse(FutureUtils.emptyCompletedFuture())
                            .get(lifecyclePhaseTimeout, lifecyclePhaseTimeunit);
                } catch (CompletionException | ExecutionException e) {
                    exceptionHandler.accept(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn(
                            "Completion interrupted during {} phase [{}]. Proceeding to following phase",
                            lifecycleState.description, currentLifecyclePhase);
                } catch (TimeoutException e) {
                    final long lifecyclePhaseTimeoutInSeconds = TimeUnit.SECONDS.convert(lifecyclePhaseTimeout,
                                                                                         lifecyclePhaseTimeunit);
                    logger.warn(
                            "Timed out during {} phase [{}] after {} second(s). Proceeding to following phase",
                            lifecycleState.description, currentLifecyclePhase, lifecyclePhaseTimeoutInSeconds);
                }
            } while ((phasedHandlers = lifecycleHandlerMap.higherEntry(currentLifecyclePhase)) != null);
            currentLifecyclePhase = null;
        }

        @Override
        public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type,
                                                    @Nonnull String name) {
            return components.getOptional(new Identifier<>(type, name))
                             .or(() -> moduleConfigurations.stream()
                                                           .map(c -> c.getOptionalComponent(type, name))
                                                           .findFirst()
                                                           .orElse(Optional.empty()));
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type,
                                  @Nonnull String name,
                                  @Nonnull Supplier<C> defaultImpl) {
            Identifier<C> identifier = new Identifier<>(type, name);
            Object component = components.computeIfAbsent(
                    identifier,
                    t -> new Component<>(identifier, config(),
                                         c -> c.getOptionalComponent(type, name).orElseGet(defaultImpl))
            ).get();
            return identifier.type().cast(component);
        }

        @Override
        public List<Module<?>> getModules() {
            List<Module<?>> modules = new ArrayList<>(DefaultRootConfigurer.this.modules);
            moduleConfigurations.forEach(moduleConfig -> modules.addAll(moduleConfig.getModules()));
            return modules;
        }

        @Override
        public void onStart(int phase, @Nonnull LifecycleHandler startHandler) {
            if (isEarlierPhaseDuringStartUp(phase)) {
                logger.info(
                        "A start handler is being registered for phase [{}] whilst phase [{}] is in progress. "
                                + "Will run provided handler immediately instead.",
                        phase, currentLifecyclePhase
                );
                requireNonNull(startHandler, "Cannot run a null start handler.").run().join();
            }
            registerLifecycleHandler(startHandlers, phase, startHandler);
        }

        private boolean isEarlierPhaseDuringStartUp(int phase) {
            return lifecycleState == LifecycleState.STARTING_UP
                    && currentLifecyclePhase != null && phase <= currentLifecyclePhase;
        }

        @Override
        public void onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
            if (isEarlierPhaseDuringShutdown(phase)) {
                logger.info(
                        "A shutdown handler is being registered for phase [{}] whilst phase [{}] is in progress. "
                                + "Will run provided handler immediately instead.",
                        phase, currentLifecyclePhase
                );
                requireNonNull(shutdownHandler, "Cannot run a null shutdown handler.").run().join();
            }
            registerLifecycleHandler(shutdownHandlers, phase, shutdownHandler);
        }

        private boolean isEarlierPhaseDuringShutdown(int phase) {
            return lifecycleState == LifecycleState.SHUTTING_DOWN
                    && currentLifecyclePhase != null && phase >= currentLifecyclePhase;
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
