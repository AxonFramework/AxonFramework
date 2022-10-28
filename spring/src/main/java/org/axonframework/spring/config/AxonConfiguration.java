/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.LifecycleHandler;
import org.axonframework.config.ModuleConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.saga.ResourceInjector;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Spring Configuration class that defines a number of conditional beans. It also allows for access to components that
 * are available in the Configuration, but not made available as Spring beans by default.
 *
 * @author Allard Buijze
 * @since 3.0
 * @deprecated Replaced by the {@link SpringConfigurer} and {@link SpringAxonConfiguration}.
 */
@org.springframework.context.annotation.Configuration("org.axonframework.spring.config.AxonConfiguration")
@Deprecated
public class AxonConfiguration implements Configuration, InitializingBean, ApplicationContextAware, SmartLifecycle {

    private final Configurer configurer;
    private Configuration config;
    private volatile boolean running = false;

    /**
     * Initializes a new {@link AxonConfiguration} that uses the given {@code configurer} to build the configuration.
     *
     * @param configurer Configuration builder for the AxonConfiguration.
     */
    public AxonConfiguration(Configurer configurer) {
        this.configurer = configurer;
    }

    @Override
    public CommandBus commandBus() {
        return config.commandBus();
    }

    @Override
    public QueryBus queryBus() {
        return config.queryBus();
    }

    @Override
    public EventBus eventBus() {
        return config.eventBus();
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return config.queryUpdateEmitter();
    }

    @NoBeanOfType(QueryBus.class)
    @Bean("queryBus")
    public QueryBus defaultQueryBus() {
        return config.queryBus();
    }

    @NoBeanOfType(QueryUpdateEmitter.class)
    @Bean("queryUpdateEmitter")
    public QueryUpdateEmitter defaultQueryUpdateEmitter() {
        return config.queryUpdateEmitter();
    }

    @NoBeanOfType(CommandBus.class)
    @Bean("commandBus")
    public CommandBus defaultCommandBus() {
        return commandBus();
    }

    @NoBeanOfType(EventBus.class)
    @Bean("eventBus")
    public EventBus defaultEventBus() {
        return eventBus();
    }

    @Override
    public ResourceInjector resourceInjector() {
        return config.resourceInjector();
    }

    @Override
    public EventProcessingConfiguration eventProcessingConfiguration() {
        return config.eventProcessingConfiguration();
    }

    /**
     * Returns the CommandGateway used to send commands to command handlers.
     *
     * @param commandBus the command bus to be used by the gateway
     * @return the CommandGateway used to send commands to command handlers
     */
    @NoBeanOfType(CommandGateway.class)
    @Bean
    public CommandGateway commandGateway(CommandBus commandBus) {
        return DefaultCommandGateway.builder().commandBus(commandBus).build();
    }

    @NoBeanOfType(QueryGateway.class)
    @Bean
    public QueryGateway queryGateway(QueryBus queryBus) {
        return DefaultQueryGateway.builder().queryBus(queryBus).build();
    }

    @Override
    public <T> Repository<T> repository(@Nonnull Class<T> aggregateType) {
        return config.repository(aggregateType);
    }

    @Override
    public <T> T getComponent(@Nonnull Class<T> componentType, @Nonnull Supplier<T> defaultImpl) {
        return config.getComponent(componentType, defaultImpl);
    }

    @Override
    public <T> List<T> getComponents(@Nonnull Class<T> componentType, @Nonnull Supplier<T> defaultImpl) {
        return config.getComponents(componentType, defaultImpl);
    }

    @Override
    public <M extends Message<?>> MessageMonitor<? super M> messageMonitor(@Nonnull Class<?> componentType,
                                                                           @Nonnull String componentName) {
        return config.messageMonitor(componentType, componentName);
    }

    @Override
    public Serializer eventSerializer() {
        return config.eventSerializer();
    }

    @Override
    public Serializer messageSerializer() {
        return config.messageSerializer();
    }

    @Override
    public List<CorrelationDataProvider> correlationDataProviders() {
        return config.correlationDataProviders();
    }

    @Override
    public HandlerDefinition handlerDefinition(Class<?> inspectedType) {
        return config.handlerDefinition(inspectedType);
    }

    @Override
    public EventUpcasterChain upcasterChain() {
        return config.upcasterChain();
    }

    @Override
    public List<ModuleConfiguration> getModules() {
        return config.getModules();
    }

    @Override
    public void onStart(int phase, LifecycleHandler startHandler) {
        config.onStart(phase, startHandler);
    }

    @Override
    public void onShutdown(int phase, LifecycleHandler shutdownHandler) {
        config.onShutdown(phase, shutdownHandler);
    }

    @Override
    public void start() {
        config.start();
        this.running = true;
    }

    @Override
    public void shutdown() {
        config.shutdown();
    }

    @Override
    public void stop() {
        shutdown();
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }


    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public void afterPropertiesSet() {
        config = configurer.buildConfiguration();
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        configurer.registerComponent(ApplicationContext.class, c -> applicationContext);
    }
}

