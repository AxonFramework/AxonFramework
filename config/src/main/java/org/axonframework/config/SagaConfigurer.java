/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.modelling.saga.AbstractSagaManager;
import org.axonframework.modelling.saga.AnnotatedSagaManager;
import org.axonframework.modelling.saga.SagaRepository;
import org.axonframework.modelling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.modelling.saga.repository.SagaStore;

import java.util.function.Function;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Provides mechanisms to configure the components used to manage and store Saga.
 *
 * @param <T> the Saga type under configuration
 * @author Allard Buijze
 * @since 4.0
 */
public class SagaConfigurer<T> {

    private final Class<T> type;
    private Function<Configuration, AbstractSagaManager<T>> managerBuilder;
    private Function<Configuration, SagaRepository<T>> repositoryBuilder;
    @SuppressWarnings("unchecked")
    private Function<Configuration, SagaStore<? super T>> storeBuilder =
            c -> c.eventProcessingConfiguration()
                  .sagaStore();
    private SagaConfigurationImpl<T> sagaConfig;

    /**
     * Retrieve the {@link SagaConfigurer} for given {@code sagaType}.
     *
     * @param sagaType the type of the Saga
     * @param <T>      a generic specifying the Saga type
     * @return a {@link SagaConfigurer} to configure a Saga with
     */
    public static <T> SagaConfigurer<T> forType(Class<T> sagaType) {
        return new SagaConfigurer<>(sagaType);
    }


    /**
     * Initializes a configurer for the given Saga Type.
     *
     * @param type the type of Saga
     */
    protected SagaConfigurer(Class<T> type) {
        verifyNotInitialized();
        assertNonNull(type, "Saga type is not allowed to be null");
        this.type = type;
    }

    /**
     * Configures a Saga Manager for this Saga.
     *
     * @param managerBuilder a {@link Function} that builds a Saga Manager
     * @return this {@link SagaConfigurer} instance, for fluent interfacing
     */
    public SagaConfigurer<T> configureSagaManager(
            Function<Configuration, AbstractSagaManager<T>> managerBuilder) {
        verifyNotInitialized();
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
    public SagaConfigurer<T> configureRepository(
            Function<Configuration, SagaRepository<T>> repositoryBuilder) {
        verifyNotInitialized();
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
    public SagaConfigurer<T> configureSagaStore(
            Function<Configuration, SagaStore<? super T>> storeBuilder) {
        verifyNotInitialized();
        assertNonNull(storeBuilder, "SagaStore builder is not allowed to be null");
        this.storeBuilder = storeBuilder;
        return this;
    }

    private void verifyNotInitialized() {
        if (this.sagaConfig != null) {
            throw new AxonConfigurationException(
                    "SagaConfiguration has already been created. Cannot make modifications.");
        }
    }

    /**
     * Initializes Saga Configuration by using the main {@link Configuration}. After initialization, it is safe to call
     * accessor methods on this Configuration.
     *
     * @param configuration the main {@link Configuration} used to provide components to this Saga Configuration
     * @return the instance describing the Saga Configuration
     */
    public SagaConfiguration<T> initialize(Configuration configuration) {
        if (this.sagaConfig == null) {
            sagaConfig = new SagaConfigurationImpl<>(this);
            sagaConfig.initialize(configuration);
        }
        return sagaConfig;
    }

    private static class SagaConfigurationImpl<S> implements SagaConfiguration<S> {

        private final SagaConfigurer<S> configurer;
        private Configuration config;
        private Component<AbstractSagaManager<S>> manager;
        private Component<SagaRepository<S>> repository;
        private Component<SagaStore<? super S>> store;

        /**
         * Creates a Saga Configuration using the given {@link SagaConfigurer}.
         *
         * @param sagaConfigurer a {@link SagaConfigurer} to build a Saga Configuration with
         */
        protected SagaConfigurationImpl(SagaConfigurer<S> sagaConfigurer) {
            this.configurer = sagaConfigurer;
        }

        @Override
        public Class<S> type() {
            return configurer.type;
        }

        @Override
        public AbstractSagaManager<S> manager() {
            ensureInitialized();
            return manager.get();
        }

        @Override
        public SagaRepository<S> repository() {
            ensureInitialized();
            return repository.get();
        }

        @Override
        public SagaStore<? super S> store() {
            ensureInitialized();
            return store.get();
        }

        @Override
        public ListenerInvocationErrorHandler listenerInvocationErrorHandler() {
            ensureInitialized();
            return config.eventProcessingConfiguration()
                         .listenerInvocationErrorHandler(processingGroup());
        }

        @Override
        @Deprecated
        public <T extends EventProcessor> T eventProcessor() {
            ensureInitialized();
            //noinspection unchecked
            return (T) config.eventProcessingConfiguration()
                             .sagaEventProcessor(configurer.type)
                             .orElseThrow(() -> new IllegalStateException(format(
                                     "Saga %s does not have a processor configured.",
                                     configurer.type)));
        }

        @Override
        public String processingGroup() {
            ensureInitialized();
            return config.eventProcessingConfiguration()
                         .sagaProcessingGroup(configurer.type);
        }

        private void initialize(Configuration configuration) {
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

        private void ensureInitialized() {
            Assert.state(config != null, () -> "Configuration is not initialized yet");
        }
    }
}
