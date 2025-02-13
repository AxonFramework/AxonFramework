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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.config.CommandBusBuilder;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventBusSpanFactory;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.configuration.MessageHandlingComponent;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusSpanFactory;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.QueryUpdateEmitterSpanFactory;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertStrictPositive;
import static org.axonframework.util.HandlerTypeResolver.*;

/**
 * Entry point of the Axon Configuration API. It implements the Configurer interface, providing access to the methods to
 * configure the default Axon components.
 * <p>
 * Using {@link #configurer()}, you will get a Configurer instance with default components configured.
 * <p>
 * Use {@link #buildConfiguration()} to build the configuration, which provides access to the configured building
 * blocks, such as the {@link CommandBus} and {@link EventBus}.
 * <p>
 * Note that this Configurer implementation is not thread-safe.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class NewDefaultConfigurer implements NewConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final Runnable NOTHING = () -> {
    };

    private final Components components = new Components();
    private final List<Module> modules = new ArrayList<>();

    private final List<Consumer<NewConfiguration>> initHandlers = new ArrayList<>();
    private final TreeMap<Integer, List<LifecycleHandler>> startHandlers = new TreeMap<>();
    private final TreeMap<Integer, List<LifecycleHandler>> shutdownHandlers = new TreeMap<>(Comparator.reverseOrder());
    private long lifecyclePhaseTimeout = 5;
    private TimeUnit lifecyclePhaseTimeunit = TimeUnit.SECONDS;

    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    private boolean initialized = false;
    private Integer currentLifecyclePhase = null;
    private LifecycleState lifecycleState = LifecycleState.DOWN;

    private final NewConfiguration config = new ConfigurationImpl();

    /**
     * Initialize the Configurer.
     */
    protected NewDefaultConfigurer() {
        components.put(CommandBus.class, new Component<>(config, "commandBus", this::defaultCommandBus));
        components.put(EventBus.class,
                       new Component<>(config, "eventBus", this::defaultEventBus));
        components.put(QueryBus.class,
                       new Component<>(config, "queryBus", this::defaultQueryBus));
        components.put(QueryUpdateEmitter.class,
                       new Component<>(config, "queryUpdateEmitter", this::defaultQueryUpdateEmitter));
        components.put(CommandGateway.class,
                       new Component<>(config, "commandGateway", this::defaultCommandGateway));
        components.put(EventGateway.class,
                       new Component<>(config, "eventGateway", this::defaultEventGateway));
        components.put(QueryGateway.class,
                       new Component<>(config, "queryGateway", this::defaultQueryGateway));
    }

    /**
     * Returns a Configurer instance with default components configured, such as a {@link SimpleCommandBus} and
     * {@link SimpleEventBus}.
     *
     * @return Configurer instance for further configuration.
     */
    public static NewConfigurer configurer() {
        return configurer(true);
    }

    /**
     * Returns a Configurer instance with default components configured, such as a {@link SimpleCommandBus} and
     * {@link SimpleEventBus}, indicating whether to {@code autoLocateConfigurerModules}.
     * <p>
     * When {@code autoLocateConfigurerModules} is {@code true}, a ServiceLoader will be used to locate all declared
     * instances of type {@link NewConfigurerModule}. Each of the discovered instances will be invoked, allowing it to
     * set default values for the configuration.
     *
     * @param autoLocateConfigurerModules flag indicating whether ConfigurerModules on the classpath should be
     *                                    automatically retrieved. Should be set to {@code false} when using an
     *                                    application container, such as Spring or CDI.
     * @return Configurer instance for further configuration.
     */
    public static NewConfigurer configurer(boolean autoLocateConfigurerModules) {
        NewDefaultConfigurer configurer = new NewDefaultConfigurer();
        if (autoLocateConfigurerModules) {
            ServiceLoader<NewConfigurerModule> configurerModuleLoader =
                    ServiceLoader.load(NewConfigurerModule.class, configurer.getClass().getClassLoader());
            List<NewConfigurerModule> configurerModules = new ArrayList<>();
            configurerModuleLoader.forEach(configurerModules::add);
            configurerModules.sort(Comparator.comparingInt(NewConfigurerModule::order));
            configurerModules.forEach(cm -> cm.configureModule(configurer));
        }
        return configurer;
    }


    protected <T> Optional<T> defaultComponent(Class<T> type, NewConfiguration configuration) {
        return Optional.empty();
    }

    /**
     * Returns a {@link DefaultCommandGateway} that will use the configuration's {@link CommandBus} to dispatch
     * commands.
     *
     * @param config The configuration that supplies the command bus.
     * @return The default command gateway.
     */
    protected CommandGateway defaultCommandGateway(NewConfiguration config) {
        return defaultComponent(CommandGateway.class, config)
                .orElseGet(() -> new DefaultCommandGateway(config.commandBus(), messageTypeResolver));
    }

    /**
     * Returns a {@link DefaultQueryGateway} that will use the configuration's {@link QueryBus} to dispatch queries.
     *
     * @param config The configuration that supplies the query bus.
     * @return The default query gateway.
     */
    protected QueryGateway defaultQueryGateway(NewConfiguration config) {
        return defaultComponent(QueryGateway.class, config)
                .orElseGet(() -> DefaultQueryGateway.builder().queryBus(config.queryBus()).build());
    }

    /**
     * Provides the default QueryBus implementations. Subclasses may override this method to provide their own default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default QueryBus to use.
     */
    protected QueryBus defaultQueryBus(NewConfiguration config) {
        return defaultComponent(QueryBus.class, config)
                .orElseGet(() -> SimpleQueryBus.builder()
                                               .transactionManager(config.getComponent(
                                                       TransactionManager.class,
                                                       NoTransactionManager::instance
                                               ))
                                               .errorHandler(config.getComponent(
                                                       QueryInvocationErrorHandler.class,
                                                       () -> LoggingQueryInvocationErrorHandler.builder()
                                                                                               .build()
                                               ))
                                               .queryUpdateEmitter(config.getComponent(QueryUpdateEmitter.class))
                                               .spanFactory(config.getComponent(QueryBusSpanFactory.class))
                                               .build());
    }

    /**
     * Provides the default QueryUpdateEmitter implementation. Subclasses may override this method to provide their own
     * default.
     *
     * @param config The configuration based on which the component is initialized
     * @return The default QueryUpdateEmitter to use
     */
    protected QueryUpdateEmitter defaultQueryUpdateEmitter(NewConfiguration config) {
        return defaultComponent(QueryUpdateEmitter.class, config)
                .orElseGet(() -> SimpleQueryUpdateEmitter.builder()
                                                         .spanFactory(config.getComponent(QueryUpdateEmitterSpanFactory.class))
                                                         .build());
    }

    /**
     * Provides the default CommandBus implementation. Subclasses may override this method to provide their own
     * default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default CommandBus to use.
     */
    protected CommandBus defaultCommandBus(NewConfiguration config) {
        return defaultComponent(CommandBus.class, config)
                .orElseGet(() -> {
                    CommandBusBuilder commandBusBuilder = CommandBusBuilder.forSimpleCommandBus();
                    TransactionManager txManager = config.getComponent(TransactionManager.class);
                    if (txManager != null) {
                        commandBusBuilder.withTransactions(txManager);
                    }
//                    if (!config.correlationDataProviders().isEmpty()) {
//                        CorrelationDataInterceptor<Message<?>> interceptor = new CorrelationDataInterceptor<>(config.correlationDataProviders());
//                        commandBusBuilder.withHandlerInterceptor(interceptor);
//                        //TODO - commandBusBuilder.withDispatchInterceptor(interceptor);
//                    }
                    return commandBusBuilder.build(config);
                });
    }

    /**
     * Provides the default EventBus implementation. Subclasses may override this method to provide their own default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default EventBus to use.
     */
    protected EventBus defaultEventBus(NewConfiguration config) {
        return defaultComponent(EventBus.class, config)
                .orElseGet(() -> SimpleEventBus.builder()
                                               .spanFactory(config.getComponent(EventBusSpanFactory.class))
                                               .build());
    }

    /**
     * Returns a {@link DefaultEventGateway} that will use the configuration's {@link EventBus} to publish events.
     *
     * @param config The configuration that supplies the event bus.
     * @return The default event gateway.
     */
    protected EventGateway defaultEventGateway(NewConfiguration config) {
        return defaultComponent(EventGateway.class, config)
                .orElseGet(() -> DefaultEventGateway.builder().eventBus(config.eventBus()).build());
    }

    @Override
    public <C> NewConfigurer registerComponent(@Nonnull Class<C> type,
                                               @Nonnull ComponentBuilder<C> componentBuilder) {
        logger.debug("Registering component [{}].", type.getSimpleName());
        components.put(type, new Component<>(config, type.getSimpleName(), componentBuilder));
        return this;
    }

    @Override
    public <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                               @Nonnull ComponentDecorator<C> decorator) {
        String name = type.getSimpleName();
        logger.debug("Registering decorator for [{}].", name);
        components.get(type).decorate(decorator);
        return this;
    }

    @Override
    public <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                               Integer order,
                                               @Nonnull ComponentDecorator<C> decorator) {
        String name = type.getSimpleName();
        logger.debug("Registering decorator for [{}] at order #{}.", name, order);
        components.get(type).decorate(order, decorator);
        return this;
    }

    @Override
    public NewConfigurer registerModule(@Nonnull Module module) {
        logger.debug("Registering module [{}]", module.getClass().getSimpleName());
        if (initialized) {
            module.initialize(config);
        }
        this.modules.add(module);
        return this;
    }

    @Override
    public <C> Optional<Component<C>> defaultComponent(@Nonnull Class<C> type) {
        return Optional.empty();
    }

    @Override
    public NewConfigurer registerCommandHandler(@Nonnull ComponentBuilder<CommandHandler> commandHandlerBuilder) {
        components.get(CommandBus.class)
                  .map(busComponent -> busComponent.decorate(
                          (config, delegate) -> (CommandBus) delegate.subscribe(
                                  new QualifiedName("TODO perhaps move QualifiedName to the method on the Configurer. Or, make the configurer into a CommandHandlerRegistry"),
                                  commandHandlerBuilder.build(config)
                          )
                  ));
        return this;
    }

    @Override
    public NewConfigurer registerQueryHandler(@Nonnull ComponentBuilder<QueryHandler> queryHandlerBuilder) {
//        messageHandlerRegistrars.add(new Component<>(
//                () -> config,
//                "QueryHandlerRegistrar",
//                configuration -> new MessageHandlerRegistrar(
//                        () -> configuration,
//                        queryHandlerBuilder,
//                        (config, queryHandler) -> new AnnotationQueryHandlerAdapter<>(
//                                queryHandler,
//                                config.parameterResolverFactory(),
//                                config.handlerDefinition(queryHandler.getClass())
//                        ).subscribe(config.queryBus())
//                )
//        ));
        return this;
    }

    @Override
    public NewConfigurer registerMessageHandlingComponent(
            @Nonnull ComponentBuilder<MessageHandlingComponent> handlingComponentBuilder
    ) {
        Component<MessageHandlingComponent> messageHandler = new Component<>(() -> config,
                                                                             "",
                                                                             handlingComponentBuilder);
        Class<?> handlerClass = messageHandler.get().getClass();
        if (isCommandHandler(handlerClass)) {
            registerCommandHandler(c -> messageHandler.get());
        }
        if (isEventHandler(handlerClass)) {
//            eventProcessing().registerEventHandler(c -> messageHandler.get());
        }
        if (isQueryHandler(handlerClass)) {
            registerQueryHandler(c -> messageHandler.get());
        }
        return this;
    }

    @Override
    public NewConfigurer configureLifecyclePhaseTimeout(long timeout, TimeUnit timeUnit) {
        assertStrictPositive(timeout, "The lifecycle phase timeout should be strictly positive");
        assertNonNull(timeUnit, "The lifecycle phase time unit should not be null");
        this.lifecyclePhaseTimeout = timeout;
        this.lifecyclePhaseTimeunit = timeUnit;
        return this;
    }

    @Override
    public NewConfiguration buildConfiguration() {
        if (!initialized) {
            verifyIdentifierFactory();
            prepareModules();
//            prepareMessageHandlerRegistrars();
            invokeInitHandlers();
        }
        return config;
    }

    /**
     * Prepare the registered modules for initialization. This ensures all lifecycle handlers are registered.
     */
    protected void prepareModules() {
        modules.forEach(module -> initHandlers.add(module::initialize));
    }

    /**
     * Verifies that a valid {@link IdentifierFactory} class has been configured.
     *
     * @throws IllegalArgumentException if the configured factory is not valid
     */
    private void verifyIdentifierFactory() {
        try {
            IdentifierFactory.getInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("The configured IdentifierFactory could not be instantiated.", e);
        }
    }

    /**
     * Calls all registered init handlers. Registration of init handlers after this invocation will result in an
     * immediate invocation of that handler.
     */
    protected void invokeInitHandlers() {
        initialized = true;
        initHandlers.forEach(h -> h.accept(config));
    }

    /**
     * Invokes all registered start handlers.
     */
    protected void invokeStartHandlers() {
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
            logger.debug("Entered {} handler lifecycle phase [{}]", lifecycleState.description, currentLifecyclePhase);

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

    /**
     * Returns the current Configuration object being built by this Configurer, without initializing it. Note that
     * retrieving objects from this configuration may lead to premature initialization of certain components.
     *
     * @return The current Configuration object being built by this Configurer.
     */
    protected NewConfiguration getConfig() {
        return config;
    }

    /**
     * Returns a map of all registered components in this configuration. The key of the map is the registered component
     * type (typically an interface), the value is a Component instance that wraps the actual implementation. Note that
     * calling {@link Component#get()} may prematurely initialize a component.
     *
     * @return A map of all registered components in this configuration.
     */
    public Map<Class<?>, Component<?>> getComponents() {
        return components.all();
    }

    @Override
    public void onStart(int phase, LifecycleHandler startHandler) {
        onInitialize(cfg -> cfg.onStart(phase, startHandler));
    }

    @Override
    public void onShutdown(int phase, LifecycleHandler shutdownHandler) {
        onInitialize(cfg -> cfg.onShutdown(phase, shutdownHandler));
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

    private class ConfigurationImpl implements NewConfiguration {

        @Override
        public <T> T getComponent(@Nonnull Class<T> componentType, @Nonnull Supplier<T> defaultImpl) {
            Object component = components.computeIfAbsent(
                    componentType,
                    type -> new Component<>(config,
                                            componentType.getSimpleName(),
                                            c -> defaultComponent(componentType, c).orElseGet(defaultImpl))
            ).get();
            return componentType.cast(component);
        }

        @Override
        public void start() {
            invokeStartHandlers();
        }

        @Override
        public void shutdown() {
            invokeShutdownHandlers();
        }

        @Override
        public List<Module> getModules() {
            return modules;
        }

        @Override
        public void onStart(int phase, LifecycleHandler startHandler) {
            if (isEarlierPhaseDuringStartUp(phase)) {
                logger.info(
                        "A start handler is being registered for phase [{}] whilst phase [{}] is in progress. "
                                + "Will run provided handler immediately instead.",
                        phase, currentLifecyclePhase
                );
                startHandler.run().join();
            }
            registerLifecycleHandler(startHandlers, phase, startHandler);
        }

        private boolean isEarlierPhaseDuringStartUp(int phase) {
            return lifecycleState == LifecycleState.STARTING_UP
                    && currentLifecyclePhase != null && phase <= currentLifecyclePhase;
        }

        @Override
        public void onShutdown(int phase, LifecycleHandler shutdownHandler) {
            if (isEarlierPhaseDuringShutdown(phase)) {
                logger.info(
                        "A shutdown handler is being registered for phase [{}] whilst phase [{}] is in progress. "
                                + "Will run provided handler immediately instead.",
                        phase, currentLifecyclePhase
                );
                shutdownHandler.run().join();
            }
            registerLifecycleHandler(shutdownHandlers, phase, shutdownHandler);
        }

        private boolean isEarlierPhaseDuringShutdown(int phase) {
            return lifecycleState == LifecycleState.SHUTTING_DOWN
                    && currentLifecyclePhase != null && phase >= currentLifecyclePhase;
        }

        private void registerLifecycleHandler(Map<Integer, List<LifecycleHandler>> lifecycleHandlers,
                                              int phase,
                                              LifecycleHandler lifecycleHandler) {
            lifecycleHandlers.compute(phase, (p, handlers) -> {
                if (handlers == null) {
                    handlers = new CopyOnWriteArrayList<>();
                }
                handlers.add(lifecycleHandler);
                return handlers;
            });
        }
    }
}
