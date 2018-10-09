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

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Represents a set of components needed to configure a Saga. This Saga Configuration should be built using the
 * {@link #forType(Class)} method. This Saga Configuration should be initialized using
 * {@link #initialize(Configuration)} after building it.
 *
 * @param <S> a generic specifying the Saga type
 * @author Milan Savic
 * @since 4.0
 */
public class SagaConfiguration<S> {

    private Configuration config;
    private final SagaConfigurer<S> configurer;
    private Component<AbstractSagaManager<S>> manager;
    private Component<SagaRepository<S>> repository;
    private Component<SagaStore<? super S>> store;

    /**
     * Creates a Saga Configuration using the given {@link SagaConfigurer}.
     *
     * @param sagaConfigurer a {@link SagaConfigurer} to build a Saga Configuration with
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
     * Retrieve the Saga Manager in this Configuration.
     *
     * @return the Manager for this Saga Configuration
     */
    public Component<AbstractSagaManager<S>> manager() {
        ensureInitialized();
        return manager;
    }

    /**
     * Retrieve the {@link SagaRepository} in this Configuration.
     *
     * @return the {@link SagaRepository} in this Configuration
     */
    public Component<SagaRepository<S>> repository() {
        ensureInitialized();
        return repository;
    }

    /**
     * Retrieve the {@link SagaStore} in this Configuration.
     *
     * @return the {@link SagaStore} in this Configuration
     */
    public Component<SagaStore<? super S>> store() {
        ensureInitialized();
        return store;
    }

    /**
     * Retrieve the Saga's {@link ListenerInvocationErrorHandler}.
     *
     * @return the Saga's {@link ListenerInvocationErrorHandler}
     */
    public ListenerInvocationErrorHandler listenerInvocationErrorHandler() {
        ensureInitialized();
        return config.eventProcessingConfiguration()
                     .listenerInvocationErrorHandler(processingGroup());
    }

    /**
     * Gets the {@link EventProcessor} for this Saga.
     *
     * @param <T> the type of the {@link EventProcessor}
     * @return the {@link EventProcessor} for this Saga
     */
    public <T extends EventProcessor> T eventProcessor() {
        ensureInitialized();
        //noinspection unchecked
        return (T) config.eventProcessingConfiguration()
                         .sagaEventProcessor(configurer.type)
                         .orElseThrow(() -> new IllegalStateException(format(
                                 "Saga %s does not have a processor configured.",
                                 configurer.type)));
    }

    /**
     * Gets the Processing Group this Saga is assigned to.
     *
     * @return the Processing Group this Saga is assigned to
     */
    public String processingGroup() {
        ensureInitialized();
        return config.eventProcessingConfiguration()
                     .sagaProcessingGroup(configurer.type);
    }

    /**
     * Initializes Saga Configuration by using the main {@link Configuration}. After initialization, it is safe to call
     * accessor methods on this Configuration.
     *
     * @param configuration the main {@link Configuration} used to provide components to this Saga Configuration
     */
    public void initialize(Configuration configuration) {
        this.config = configuration;
        String managerName = configurer.type.getSimpleName() + "Manager";
        String repositoryName = configurer.type.getSimpleName() + "Repository";
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
            managerBuilder = c -> {
                EventProcessingConfiguration eventProcessingConfiguration = c.eventProcessingConfiguration();
                return AnnotatedSagaManager.<S>builder()
                        .sagaType(configurer.type)
                        .sagaRepository(repository.get())
                        .parameterResolverFactory(c.parameterResolverFactory())
                        .handlerDefinition(c.handlerDefinition(configurer.type))
                        .listenerInvocationErrorHandler(eventProcessingConfiguration.listenerInvocationErrorHandler(
                                processingGroup()))
                        .build();
            };
        }
        manager = new Component<>(configuration, managerName, managerBuilder);
    }

    /**
     * Retrieve the {@link SagaConfigurer} for given {@code sagaType}.
     *
     * @param sagaType the type of the Saga
     * @param <T>      a generic specifying the Saga type
     * @return a {@link SagaConfigurer} to configure a Saga with
     */
    public static <T> SagaConfigurer<T> forType(Class<T> sagaType) {
        return new SagaConfigurer<T>().type(sagaType);
    }

    /**
     * Builds the default Saga Configuration for given {@code sagaType}. Defaults are taken from {@link
     * EventProcessingConfiguration}.
     *
     * @param sagaType the type of Saga
     * @param <T>      a generic specifying the Saga type
     * @return a default Saga Configuration
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
     * @param <T> a generic specifying the Saga type
     */
    public static class SagaConfigurer<T> {

        private Class<T> type;
        private Function<Configuration, AbstractSagaManager<T>> managerBuilder;
        private Function<Configuration, SagaRepository<T>> repositoryBuilder;
        @SuppressWarnings("unchecked")
        private Function<Configuration, SagaStore<? super T>> storeBuilder =
                c -> c.eventProcessingConfiguration()
                      .sagaStore();

        /**
         * Configures the Saga Type.
         *
         * @param type the type of Saga
         * @return this {@link SagaConfigurer} instance, for fluent interfacing
         */
        public SagaConfigurer<T> type(Class<T> type) {
            assertNonNull(type, "Saga type is not allowed to be null");
            this.type = type;
            return this;
        }

        /**
         * Configures a Saga Manager for this Saga.
         *
         * @param managerBuilder a {@link Function} that builds a Saga Manager
         * @return this {@link SagaConfigurer} instance, for fluent interfacing
         */
        public SagaConfigurer<T> managerBuilder(
                Function<Configuration, AbstractSagaManager<T>> managerBuilder) {
            assertNonNull(managerBuilder, "SagaManager builder is not allowed to be null");
            this.managerBuilder = managerBuilder;
            return this;
        }

        /**
         * Configures a {@link SagaRepository} for this Saga.
         *
         * @param repositoryBuilder a {@link Function} that builds {@link SagaRepository}
         * @return this {@link SagaConfigurer} instance, for fluent interfacing
         */
        public SagaConfigurer<T> repositoryBuilder(
                Function<Configuration, SagaRepository<T>> repositoryBuilder) {
            assertNonNull(repositoryBuilder, "SagaRepository builder is not allowed to be null");
            this.repositoryBuilder = repositoryBuilder;
            return this;
        }

        /**
         * Configures a {@link SagaStore} for this Saga.
         *
         * @param storeBuilder a {@link Function} that builds {@link SagaStore}
         * @return this {@link SagaConfigurer} instance, for fluent interfacing
         */
        public SagaConfigurer<T> storeBuilder(
                Function<Configuration, SagaStore<? super T>> storeBuilder) {
            assertNonNull(storeBuilder, "SagaStore builder is not allowed to be null");
            this.storeBuilder = storeBuilder;
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
         * Validates this Configurer. The {@code type} is the only required field.
         */
        protected void validate() {
            assertNonNull(type, "Saga type is not allowed to be null");
        }
    }
}
