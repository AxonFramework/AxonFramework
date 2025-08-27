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

package org.axonframework.springboot.autoconfig;


import io.axoniq.axonserver.connector.control.ControlChannel;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConfigurationEnhancer;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.TopologyChangeListener;
import org.axonframework.commandhandling.distributed.DistributedCommandBusConfiguration;
import org.axonframework.configuration.ComponentDecorator;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.lifecycle.Phase;
import org.axonframework.springboot.service.connection.AxonServerConnectionDetails;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Base Axon Server Autoconfiguration.
 * <p>
 * Constructs the {@link AxonServerConfiguration}, allowing for further configuration of Axon Server components through
 * property files or complete disablement of Axon Server.
 *
 * @author Marc Gathier
 * @since 4.0.0
 */
@AutoConfiguration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(AxonServerConfiguration.class)
@EnableConfigurationProperties(AxonServerConfiguration.class)
public class AxonServerAutoConfiguration implements ApplicationContextAware {

    /**
     * Constant specifying the order used to
     * {@link ComponentRegistry#registerDecorator(Class, int, ComponentDecorator) decorate} the
     * {@link AxonServerConfiguration} and {@link DistributedCommandBusConfiguration} with specifics of the Spring Boot
     * autoconfiguration.
     */
    public static final int AXON_SERVER_CONFIGURATION_ENHANCEMENT_ORDER = -100;

    private ApplicationContext applicationContext;

    @Bean
    @ConditionalOnProperty(name = "axon.axonserver.enabled", havingValue = "false")
    public ConfigurationEnhancer disableAxonServerConfigurationEnhancer() {
        return new ConfigurationEnhancer() {
            @Override
            public void enhance(@Nonnull ComponentRegistry registry) {
                registry.disableEnhancer(AxonServerConfigurationEnhancer.class);
            }

            @Override
            public int order() {
                return Integer.MIN_VALUE;
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "axon.axonserver.enabled", matchIfMissing = true)
    public ConfigurationEnhancer axonServerConfigurationEnhancer() {
        return registry -> registry.registerDecorator(
                                           AxonServerConfiguration.class,
                                           AXON_SERVER_CONFIGURATION_ENHANCEMENT_ORDER,
                                           (config, name, axonServerConfig) -> {
                                               axonServerConfig.setComponentName(clientName(applicationContext.getId()));
                                               return axonServerConfig;
                                           }
                                   )
                                   .registerDecorator(
                                           DistributedCommandBusConfiguration.class,
                                           AXON_SERVER_CONFIGURATION_ENHANCEMENT_ORDER,
                                           (config, name, distributedCommandBusConfig) -> {
                                               AxonServerConfiguration serverConfig =
                                                       config.getComponent(AxonServerConfiguration.class);
                                               int commandThreads = serverConfig.getCommandThreads();
                                               return distributedCommandBusConfig.numberOfThreads(commandThreads);
                                           }
                                   );
    }

    private static String clientName(@Nullable String id) {
        if (id == null) {
            return "Unnamed";
        } else if (id.contains(":")) {
            return id.substring(0, id.indexOf(":"));
        }
        return id;
    }

    @Bean
    @ConditionalOnBean(AxonServerConnectionDetails.class)
    @ConditionalOnProperty(name = "axon.axonserver.enabled", matchIfMissing = true)
    public ConfigurationEnhancer axonServerConfigurationWithConnectionDetails(
            AxonServerConnectionDetails connectionDetails
    ) {
        return registry -> registry.registerDecorator(
                AxonServerConfiguration.class,
                AXON_SERVER_CONFIGURATION_ENHANCEMENT_ORDER,
                (config, name, axonServerConfig) -> {
                    axonServerConfig.setServers(connectionDetails.routingServers());
                    return axonServerConfig;
                }
        );
    }

    @Bean
    @ConditionalOnProperty(name = "axon.axonserver.enabled", matchIfMissing = true)
    public ConfigurationEnhancer topologyChangeListenerConfigurerModule(List<TopologyChangeListener> changeListeners) {
        // ConditionalOnBean does not work for collections of beans, as it simply creates an empty collection.
        if (changeListeners.isEmpty()) {
            return registry -> {/*No-op*/};
        }

        DecoratorDefinition<AxonServerConnectionManager, AxonServerConnectionManager> topologyRegistrationDecorator =
                DecoratorDefinition.forType(AxonServerConnectionManager.class)
                                   .with((config, name, delegate) -> delegate)
                                   .onStart(Phase.INSTRUCTION_COMPONENTS, connectionManager -> {
                                                ControlChannel defaultControlChannel =
                                                        connectionManager.getConnection().controlChannel();
                                                changeListeners.forEach(defaultControlChannel::registerTopologyChangeHandler);
                                            }
                                   );
        return registry -> registry.registerDecorator(topologyRegistrationDecorator);
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
