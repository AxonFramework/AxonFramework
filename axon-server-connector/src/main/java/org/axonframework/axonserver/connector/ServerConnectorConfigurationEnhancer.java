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

import org.axonframework.axonserver.connector.event.AxonServerEventStorageEngine;
import org.axonframework.axonserver.connector.event.AxonServerEventStorageEngineFactory;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ConfigurationEnhancer;
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
public class ServerConnectorConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * The {@link #order()} when this {@link ServerConnectorConfigurationEnhancer} enhances an
     * {@link org.axonframework.configuration.ApplicationConfigurer}.
     */
    public static final int ENHANCER_ORDER = Integer.MIN_VALUE + 10;

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerIfNotPresent(Converter.class, c -> new TestConverter())
                .registerIfNotPresent(AxonServerConfiguration.class, c -> new AxonServerConfiguration())
                .registerIfNotPresent(connectionManagerDefinition())
                .registerIfNotPresent(ManagedChannelCustomizer.class, c -> ManagedChannelCustomizer.identity())
                .registerIfNotPresent(eventStorageEngineDefinition())
                .registerFactory(new AxonServerEventStorageEngineFactory());
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

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }
}
