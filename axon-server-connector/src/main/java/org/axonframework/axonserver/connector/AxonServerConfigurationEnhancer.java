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
import org.axonframework.axonserver.connector.event.AxonServerEventStorageEngineFactory;
import org.axonframework.axonserver.connector.event.EventProcessorControlService;
import org.axonframework.axonserver.connector.query.AxonServerQueryBusConnector;
import org.axonframework.messaging.commandhandling.distributed.CommandBusConnector;
import org.axonframework.messaging.commandhandling.distributed.PayloadConvertingCommandBusConnector;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.ComponentDecorator;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.ComponentLifecycleHandler;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.common.configuration.DecoratorDefinition;
import org.axonframework.common.configuration.SearchScope;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.common.lifecycle.Phase;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.queryhandling.distributed.PayloadConvertingQueryBusConnector;
import org.axonframework.messaging.queryhandling.distributed.QueryBusConnector;

import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * A {@link ConfigurationEnhancer} that is auto-loadable by the
 * {@link ApplicationConfigurer}, setting sensible defaults when using Axon Server.
 *
 * @author Allard Buijze
 * @since 4.0.0
 */
public class AxonServerConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * The {@link #order()} when this {@link AxonServerConfigurationEnhancer} enhances an
     * {@link ApplicationConfigurer}.
     */
    public static final int ENHANCER_ORDER = Integer.MIN_VALUE + 10;

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerIfNotPresent(AxonServerConfiguration.class,
                                      c -> new AxonServerConfiguration(),
                                      SearchScope.ALL)
                .registerIfNotPresent(connectionManagerDefinition(), SearchScope.ALL)
                .registerIfNotPresent(ManagedChannelCustomizer.class,
                                      c -> ManagedChannelCustomizer.identity(),
                                      SearchScope.ALL)
                .registerIfNotPresent(eventStorageEngineDefinition(), SearchScope.ALL)
                .registerIfNotPresent(commandBusConnectorDefinition(), SearchScope.ALL)
                .registerIfNotPresent(queryBusConnectorDefinition(), SearchScope.ALL)
                .registerDecorator(CommandBusConnector.class,
                                   0,
                                   payloadConvertingConnectorComponentDecorator()
                )
                .registerDecorator(QueryBusConnector.class,
                                   0,
                                   payloadConvertingQueryBusConnectorComponentDecorator()
                )
                .registerDecorator(topologyChangeListenerRegistration())
                .registerFactory(new AxonServerEventStorageEngineFactory())
                .registerIfNotPresent(eventProcessorControlService());
    }

    private static ComponentDefinition<AxonServerConnectionManager> connectionManagerDefinition() {
        return ComponentDefinition.ofType(AxonServerConnectionManager.class)
                                  .withBuilder(AxonServerConfigurationEnhancer::buildConnectionManager)
                                  .onStart(Phase.INSTRUCTION_COMPONENTS, AxonServerConnectionManager::start)
                                  .onShutdown(Phase.EXTERNAL_CONNECTIONS, AxonServerConnectionManager::shutdown);
    }

    private static AxonServerConnectionManager buildConnectionManager(Configuration config) {
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

    private static ComponentDefinition<EventStorageEngine> eventStorageEngineDefinition() {
        return ComponentDefinition.ofType(EventStorageEngine.class)
                                  .withBuilder(config -> {
                                      String defaultContext = config.getComponent(AxonServerConfiguration.class)
                                                                    .getContext();
                                      return AxonServerEventStorageEngineFactory.constructForContext(
                                              defaultContext,
                                              config
                                      );
                                  });
    }

    private static ComponentDefinition<CommandBusConnector> commandBusConnectorDefinition() {
        return ComponentDefinition.ofType(CommandBusConnector.class)
                                  .withBuilder(config -> new AxonServerCommandBusConnector(
                                          config.getComponent(AxonServerConnectionManager.class).getConnection(),
                                          config.getComponent(AxonServerConfiguration.class)
                                  ))
                                  .onStart(Phase.INBOUND_COMMAND_CONNECTOR,
                                           connector -> ((AxonServerCommandBusConnector) connector).start())
                                  .onShutdown(Phase.INBOUND_COMMAND_CONNECTOR,
                                              (ComponentLifecycleHandler<CommandBusConnector>) (config, connector) ->
                                                      ((AxonServerCommandBusConnector) connector).disconnect())
                                  .onShutdown(Phase.OUTBOUND_COMMAND_CONNECTORS,
                                              (ComponentLifecycleHandler<CommandBusConnector>) (config, connector) ->
                                                      ((AxonServerCommandBusConnector) connector).shutdownDispatching());
    }

    private static ComponentDefinition<QueryBusConnector> queryBusConnectorDefinition() {
        return ComponentDefinition.ofType(QueryBusConnector.class)
                                  .withBuilder(config -> new AxonServerQueryBusConnector(
                                          config.getComponent(AxonServerConnectionManager.class).getConnection(),
                                          config.getComponent(AxonServerConfiguration.class)
                                  ))
                                  .onStart(Phase.INBOUND_QUERY_CONNECTOR,
                                           connector -> ((AxonServerQueryBusConnector) connector).start())
                                  .onShutdown(Phase.INBOUND_QUERY_CONNECTOR,
                                              (ComponentLifecycleHandler<QueryBusConnector>) (config, connector) ->
                                                      ((AxonServerQueryBusConnector) connector).disconnect())
                                  .onShutdown(Phase.OUTBOUND_QUERY_CONNECTORS,
                                              (ComponentLifecycleHandler<QueryBusConnector>) (config, connector) ->
                                                      ((AxonServerQueryBusConnector) connector).shutdownDispatching());
    }

    private static ComponentDecorator<QueryBusConnector, PayloadConvertingQueryBusConnector> payloadConvertingQueryBusConnectorComponentDecorator() {
        return (config, name, delegate) -> new PayloadConvertingQueryBusConnector(
                delegate,
                config.getComponent(MessageConverter.class),
                byte[].class
        );
    }

    private static ComponentDecorator<CommandBusConnector, PayloadConvertingCommandBusConnector> payloadConvertingConnectorComponentDecorator() {
        return (config, name, delegate) -> new PayloadConvertingCommandBusConnector(
                delegate,
                config.getComponent(MessageConverter.class),
                byte[].class
        );
    }

    private static DecoratorDefinition<AxonServerConnectionManager, AxonServerConnectionManager> topologyChangeListenerRegistration() {
        return DecoratorDefinition.forType(AxonServerConnectionManager.class)
                                  .with((config, name, delegate) -> delegate)
                                  .onStart(Phase.INSTRUCTION_COMPONENTS, (config, connectionManager) -> {
                                      Optional<TopologyChangeListener> topologyChangeListener =
                                              config.getOptionalComponent(TopologyChangeListener.class);
                                      topologyChangeListener.ifPresent(
                                              changeListener -> connectionManager.getConnection()
                                                                                 .controlChannel()
                                                                                 .registerTopologyChangeHandler(
                                                                                         changeListener)
                                      );
                                      return FutureUtils.emptyCompletedFuture();
                                  });
    }

    private static ComponentDefinition<EventProcessorControlService> eventProcessorControlService() {
        return ComponentDefinition.ofType(EventProcessorControlService.class)
                                  .withBuilder(c -> {
                                      AxonServerConfiguration serverConfig =
                                              c.getComponent(AxonServerConfiguration.class);
                                      return new EventProcessorControlService(
                                              c, c.getComponent(AxonServerConnectionManager.class),
                                              serverConfig.getContext(),
                                              serverConfig.getEventhandling().getProcessors()
                                      );
                                  })
                                  .onStart(Phase.INSTRUCTION_COMPONENTS, EventProcessorControlService::start);
    }

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }
}
