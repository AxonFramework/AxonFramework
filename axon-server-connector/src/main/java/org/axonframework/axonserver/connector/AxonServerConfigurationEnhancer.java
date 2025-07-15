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

package org.axonframework.axonserver.connector;

import org.axonframework.axonserver.connector.command.AxonServerCommandBusConnector;
import org.axonframework.axonserver.connector.command.AxonServerCommandBusConnectorConfiguration;
import org.axonframework.axonserver.connector.event.AxonServerEventStorageEngine;
import org.axonframework.axonserver.connector.event.AxonServerEventStorageEngineFactory;
import org.axonframework.commandhandling.annotation.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.CommandPriorityCalculator;
import org.axonframework.commandhandling.distributed.PayloadConvertingCommandBusConnector;
import org.axonframework.commandhandling.RoutingStrategy;
import org.axonframework.configuration.ComponentDecorator;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.SearchScope;
import org.axonframework.lifecycle.Phase;
import org.axonframework.serialization.Converter;

import javax.annotation.Nonnull;

/**
 * A {@link ConfigurationEnhancer} that is auto-loadable by the
 * {@link org.axonframework.configuration.ApplicationConfigurer}, setting sensible defaults when using Axon Server.
 *
 * @author Allard Buijze
 * @since 4.0.0
 */
public class AxonServerConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * The {@link #order()} when this {@link AxonServerConfigurationEnhancer} enhances an
     * {@link org.axonframework.configuration.ApplicationConfigurer}.
     */
    public static final int ENHANCER_ORDER = Integer.MIN_VALUE + 10;

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerIfNotPresent(AxonServerConfiguration.class,
                                      c -> new AxonServerConfiguration(),
                                      SearchScope.ALL)
                .registerIfNotPresent(AxonServerCommandBusConnectorConfiguration.class,
                                      c -> new AxonServerCommandBusConnectorConfiguration(),
                                      SearchScope.ALL)
                .registerIfNotPresent(connectionManagerDefinition(), SearchScope.ALL)
                .registerIfNotPresent(ManagedChannelCustomizer.class, c -> ManagedChannelCustomizer.identity(), SearchScope.ALL)
                .registerIfNotPresent(eventStorageEngineDefinition(), SearchScope.ALL)
                .registerIfNotPresent(commandPriorityResolverDefinition(), SearchScope.ALL)
                .registerIfNotPresent(routingStrategyDefinition(), SearchScope.ALL)
                .registerFactory(new AxonServerEventStorageEngineFactory())
                .registerIfNotPresent(commandBusConnectorDefinition(), SearchScope.ALL)
                .registerDecorator(CommandBusConnector.class, 0, getPayloadConvertingConnectorComponentDecorator());
    }

    private ComponentDecorator<CommandBusConnector, PayloadConvertingCommandBusConnector<Object>> getPayloadConvertingConnectorComponentDecorator() {
        return (config, name, delegate) -> new PayloadConvertingCommandBusConnector<>(
                delegate,
                config.getComponent(Converter.class),
                byte[].class);
    }

    private ComponentDefinition<AxonServerConnectionManager> connectionManagerDefinition() {
        return ComponentDefinition.ofType(AxonServerConnectionManager.class)
                                  .withBuilder(this::buildConnectionManager)
                                  .onStart(Phase.INSTRUCTION_COMPONENTS, AxonServerConnectionManager::start)
                                  .onShutdown(Phase.EXTERNAL_CONNECTIONS, AxonServerConnectionManager::shutdown);
    }

    private AxonServerConnectionManager buildConnectionManager(Configuration config) {
        AxonServerConfiguration serverConfig = config.getComponent(AxonServerConfiguration.class);
        return AxonServerConnectionManager.builder()
                                          .routingServers(serverConfig.getServers())
                                          .axonServerConfiguration(serverConfig)
                                          .tagsConfiguration(
                                                  config.getComponent(TagsConfiguration.class, TagsConfiguration::new)
                                          )
                                          .channelCustomizer(config.getComponent(ManagedChannelCustomizer.class))
                                          .build();
    }

    private ComponentDefinition<AxonServerEventStorageEngine> eventStorageEngineDefinition() {
        return ComponentDefinition.ofType(AxonServerEventStorageEngine.class)
                                  .withBuilder(config -> {
                                      String defaultContext = config.getComponent(AxonServerConfiguration.class)
                                                                    .getContext();
                                      return AxonServerEventStorageEngineFactory.constructForContext(
                                              defaultContext,
                                              config
                                      );
                                  });
    }

    private ComponentDefinition<CommandBusConnector> commandBusConnectorDefinition() {
        return ComponentDefinition.ofType(CommandBusConnector.class)
                                  .withBuilder(config -> new AxonServerCommandBusConnector(
                                          config.getComponent(AxonServerConnectionManager.class).getConnection(),
                                           config.getComponent(AxonServerCommandBusConnectorConfiguration.class)
                                  ));
    }

    private ComponentDefinition<CommandPriorityCalculator> commandPriorityResolverDefinition() {
        return ComponentDefinition.ofType(CommandPriorityCalculator.class)
                                  .withBuilder(config -> message -> 0);
    }

    private ComponentDefinition<RoutingStrategy> routingStrategyDefinition() {
        return ComponentDefinition.ofType(RoutingStrategy.class)
                                  .withBuilder(config -> new AnnotationRoutingStrategy());
    }

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }
}
