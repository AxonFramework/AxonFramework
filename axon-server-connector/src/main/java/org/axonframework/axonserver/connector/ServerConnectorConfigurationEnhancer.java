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

import org.axonframework.config.TagsConfiguration;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * A {@link ConfigurationEnhancer} that is auto-loadable by the
 * {@link org.axonframework.configuration.ApplicationConfigurer}, setting sensible defaults when using Axon Server.
 *
 * @author Allard Buijze
 * @since 4.0.0
 */
public class ServerConnectorConfigurationEnhancer implements ConfigurationEnhancer {

    private static final Logger logger = LoggerFactory.getLogger(ServerConnectorConfigurationEnhancer.class);

    /**
     * The {@link #order()} when this {@link ServerConnectorConfigurationEnhancer} enhances an
     * {@link org.axonframework.configuration.ApplicationConfigurer}.
     */
    public static final int ENHANCER_ORDER = Integer.MIN_VALUE + 10;

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registerIfNotPresent(registry, AxonServerConfiguration.class, c -> new AxonServerConfiguration());
        registerIfNotPresent(registry, AxonServerConnectionManager.class, this::buildAxonServerConnectionManager);
        registerIfNotPresent(registry, ManagedChannelCustomizer.class, c -> ManagedChannelCustomizer.identity());
        // TODO add engine factory for context names
    }

    private <C> void registerIfNotPresent(ComponentRegistry registry,
                                          Class<C> type,
                                          ComponentFactory<C> factory) {
        if (!registry.hasComponent(type)) {
            registry.registerComponent(type, factory);
        }
    }

    private AxonServerConnectionManager buildAxonServerConnectionManager(Configuration config) {
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

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }
}
