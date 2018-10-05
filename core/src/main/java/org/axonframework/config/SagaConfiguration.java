/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.config;

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.saga.AbstractSagaManager;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.eventhandling.saga.repository.SagaStore;

import java.util.Optional;
import java.util.function.Function;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Represents a set of components needed to configure a saga. Saga configuration should be built using {@link
 * #forType(Class)} method. Saga configuration should be initialized using {@link #initialize(Configuration)} after
 * building it.
 *
 * @param <S> The type of saga
 * @author Milan Savic
 * @since 4.0
 */
public class SagaConfiguration<S> {

    private Configuration config;
    private final SagaConfigurer<S> configurer;
    private Component<AbstractSagaManager<S>> manager;
    private Component<SagaRepository<S>> repository;
    private Component<SagaStore<? super S>> store;
    private Component<ListenerInvocationErrorHandler> listenerInvocationErrorHandler;

    /**
     * Creates a Saga Configuration using Saga Configurer.
     *
     * @param sagaConfigurer a configurer for Saga Configuration
     */
    protected SagaConfiguration(SagaConfigurer<S> sagaConfigurer) {
        sagaConfigurer.validate();
        this.configurer = sagaConfigurer;
    }

    /**
     * Gets the Saga Type.
     *
     * @return the Saga Type
     */
    public Class<S> type() {
        return configurer.type;
    }

    /**
     * Gets the Saga Manager.
     *
     * @return the Manager for this Saga
     */
    public Component<AbstractSagaManager<S>> manager() {
        ensureInitialized();
        return manager;
    }

    /**
     * Gets the Saga Repository.
     *
     * @return the Saga Repository
     */
    public Component<SagaRepository<S>> repository() {
        ensureInitialized();
        return repository;
    }

    /**
     * Gets the Saga Store.
     *
     * @return the Saga Store
     */
    public Component<SagaStore<? super S>> store() {
        ensureInitialized();
        return store;
    }

    /**
     * Gets the Saga Listener Invocation Error Handler.
     *
     * @return the Saga Listener Invocation Error Handler
     */
    public Component<ListenerInvocationErrorHandler> listenerInvocationErrorHandler() {
        ensureInitialized();
        return listenerInvocationErrorHandler;
    }

    /**
     * Gets the Event Processor for this Saga.
     *
     * @param <T> The Event Processor type
     * @return the Event Processor for this Saga
     */
    public <T extends EventProcessor> T eventProcessor() {
        ensureInitialized();
        //noinspection unchecked
        return (T) config.eventProcessingConfiguration()
                         .eventProcessor(this)
                         .get();
    }

    /**
     * Gets the Processing Group this Saga is assigned to (if it is explicitly set).
     *
     * @return the Processing Group this Saga is assigned to
     */
    public Optional<String> processingGroup() {
        return Optional.ofNullable(configurer.processingGroup);
    }

    /**
     * Initializes Saga Configuration with main Configuration. After initializing it is safe to call accessor methods on
     * this Configuration.
     *
     * @param configuration The main Configuration
     */
    public void initialize(Configuration configuration) {
        this.config = configuration;
        String managerName = configurer.type.getSimpleName() + "Manager";
        String repositoryName = configurer.type.getSimpleName() + "Repository";
        listenerInvocationErrorHandler = new Component<>(configuration,
                                                         "listenerInvocationErrorHandler",
                                                         configurer.listenerInvocationErrorHandlerBuilder);
        store = new Component<>(configuration, "sagaStore", configurer.storeBuilder);
        Function<Configuration, SagaRepository<S>> repositoryBuilder = configurer.repositoryBuilder;
        if (repositoryBuilder == null) {
            repositoryBuilder = c -> AnnotatedSagaRepository.<S>builder()
                    .sagaType(configurer.type)
                    .sagaStore(store.get())
                    .resourceInjector(c.resourceInjector())
                    .parameterResolverFactory(c.parameterResolverFactory())
                    .handlerDefinition(c.handlerDefinition(configurer.type))
                    .build();
        }
        repository = new Component<>(configuration, repositoryName, repositoryBuilder);

        Function<Configuration, AbstractSagaManager<S>> managerBuilder = configurer.managerBuilder;
        if (managerBuilder == null) {
            managerBuilder = c -> AnnotatedSagaManager.<S>builder()
                    .sagaType(configurer.type)
                    .sagaRepository(repository.get())
                    .parameterResolverFactory(c.parameterResolverFactory())
                    .handlerDefinition(c.handlerDefinition(configurer.type))
                    .listenerInvocationErrorHandler(listenerInvocationErrorHandler.get())
                    .build();
        }
        manager = new Component<>(configuration, managerName, managerBuilder);
    }

    /**
     * Gets Saga Configurer for given {@code sagaType}.
     *
     * @param sagaType The type of the Saga
     * @param <T>      The type of the Saga
     * @return the Saga Configurer to configure a Saga
     */
    public static <T> SagaConfigurer<T> forType(Class<T> sagaType) {
        return new SagaConfigurer<T>().type(sagaType);
    }

    /**
     * Builds the default Saga Configuration for given {@code sagaType}. Defaults are taken from {@link
     * EventProcessingConfiguration}.
     *
     * @param sagaType The type of Saga
     * @param <T>      The type of Saga
     * @return default Saga Configuration
     */
    public static <T> SagaConfiguration<T> defaultConfiguration(Class<T> sagaType) {
        return forType(sagaType).configure();
    }

    private void ensureInitialized() {
        Assert.state(config != null, () -> "Configuration is not initialized yet");
    }

    /**
     * Provides mechanisms to configure a {@link SagaConfiguration}.
     *
     * @param <T> The type of Saga
     */
    public static class SagaConfigurer<T> {

        private Class<T> type;
        private String processingGroup;
        private Function<Configuration, AbstractSagaManager<T>> managerBuilder;
        private Function<Configuration, SagaRepository<T>> repositoryBuilder;
        @SuppressWarnings("unchecked")
        private Function<Configuration, SagaStore<? super T>> storeBuilder =
                c -> c.eventProcessingConfiguration()
                      .sagaStore();
        private Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder =
                c -> c.eventProcessingConfiguration()
                      .listenerInvocationErrorHandler(processingGroup);

        /**
         * Configures a Saga Type.
         *
         * @param type The type of Saga
         * @return this Configurer for fluent interfacing
         */
        public SagaConfigurer<T> type(Class<T> type) {
            assertNonNull(type, "Saga type is not allowed to be null");
            this.type = type;
            return this;
        }

        /**
         * Configures a Processing Group.
         *
         * @param processingGroup The Processing Group name
         * @return this Configurer for fluent interfacing
         */
        public SagaConfigurer<T> processingGroup(String processingGroup) {
            assertNonNull(processingGroup, "Processing group is not allowed to be null");
            this.processingGroup = processingGroup;
            return this;
        }

        /**
         * Configures a Saga Manager.
         *
         * @param managerBuilder A function that builds Saga Manager
         * @return this Configurer for fluent interfacing
         */
        public SagaConfigurer<T> managerBuilder(
                Function<Configuration, AbstractSagaManager<T>> managerBuilder) {
            assertNonNull(managerBuilder, "Saga manager builder is not allowed to be null");
            this.managerBuilder = managerBuilder;
            return this;
        }

        /**
         * Configures a Saga Repository.
         *
         * @param repositoryBuilder A function that builds Saga Repository
         * @return this Configurer for fluent interfacing
         */
        public SagaConfigurer<T> repositoryBuilder(
                Function<Configuration, SagaRepository<T>> repositoryBuilder) {
            assertNonNull(repositoryBuilder, "Saga repository builder is not allowed to be null");
            this.repositoryBuilder = repositoryBuilder;
            return this;
        }

        /**
         * Configures a Saga Store.
         *
         * @param storeBuilder A function that builds Saga Store
         * @return this Configurer for fluent interfacing
         */
        public SagaConfigurer<T> storeBuilder(
                Function<Configuration, SagaStore<? super T>> storeBuilder) {
            assertNonNull(storeBuilder, "Saga store builder is not allowed to be null");
            this.storeBuilder = storeBuilder;
            return this;
        }

        /**
         * Configures a Saga Listener Invocation Error Handler.
         *
         * @param listenerInvocationErrorHandlerBuilder A function that builds Saga Listener Invocation Error Handler
         * @return this Configurer for fluent interfacing
         */
        public SagaConfigurer<T> listenerInvocationHandler(
                Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder) {
            assertNonNull(listenerInvocationErrorHandlerBuilder,
                          "Listener invocation error handler builder is not allowed to be null");
            this.listenerInvocationErrorHandlerBuilder = listenerInvocationErrorHandlerBuilder;
            return this;
        }

        /**
         * Builds Saga Configuration with provided configuration components.
         *
         * @return a Saga Configuration instance
         */
        public SagaConfiguration<T> configure() {
            return new SagaConfiguration<>(this);
        }

        /**
         * Validates this Configurer. {@code type} is the only required field.
         */
        protected void validate() {
            assertNonNull(type, "Saga type is not allowed to be null");
        }
    }
}
