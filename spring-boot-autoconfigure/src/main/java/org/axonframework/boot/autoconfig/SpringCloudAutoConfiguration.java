/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.boot.autoconfig;

import org.axonframework.boot.DistributedCommandBusProperties;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.CommandRouter;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.serialization.Serializer;
import org.axonframework.springcloud.commandhandling.SpringCloudCommandRouter;
import org.axonframework.springcloud.commandhandling.SpringCloudHttpBackupCommandRouter;
import org.axonframework.springcloud.commandhandling.SpringHttpCommandBusConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.noop.NoopDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
@AutoConfigureAfter({
        RoutingStrategyAutoConfiguration.class,
        NoopDiscoveryClientAutoConfiguration.class,
        SimpleDiscoveryClientAutoConfiguration.class
})
@AutoConfigureBefore(JGroupsAutoConfiguration.class)
@ConditionalOnProperty("axon.distributed.enabled")
@ConditionalOnClass(name = {
        "org.axonframework.springcloud.commandhandling.SpringCloudCommandRouter",
        "org.axonframework.springcloud.commandhandling.SpringCloudHttpBackupCommandRouter",
        "org.axonframework.springcloud.commandhandling.SpringHttpCommandBusConnector",
        "org.springframework.cloud.client.discovery.DiscoveryClient",
        "org.springframework.web.client.RestTemplate"
})
public class SpringCloudAutoConfiguration {

    @Autowired
    private DistributedCommandBusProperties properties;

    @Bean
    @Primary
    @ConditionalOnMissingBean(CommandRouter.class)
    @ConditionalOnBean(DiscoveryClient.class)
    @ConditionalOnProperty(value = "axon.distributed.spring-cloud.fallback-to-http-get", matchIfMissing = true)
    public SpringCloudHttpBackupCommandRouter springCloudHttpBackupCommandRouter(DiscoveryClient discoveryClient,
                                                                                 Registration localServiceInstance,
                                                                                 RestTemplate restTemplate,
                                                                                 RoutingStrategy routingStrategy) {
        return new SpringCloudHttpBackupCommandRouter(discoveryClient,
                                                      localServiceInstance,
                                                      routingStrategy,
                                                      restTemplate,
                                                      properties.getSpringCloud().getFallbackUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DiscoveryClient.class)
    public CommandRouter springCloudCommandRouter(DiscoveryClient discoveryClient,
                                                  Registration localServiceInstance,
                                                  RoutingStrategy routingStrategy) {
        return new SpringCloudCommandRouter(discoveryClient, localServiceInstance, routingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean(CommandBusConnector.class)
    public SpringHttpCommandBusConnector springHttpCommandBusConnector(
            @Qualifier("localSegment") CommandBus localSegment,
            RestTemplate restTemplate,
            @Qualifier("messageSerializer") Serializer serializer) {
        return new SpringHttpCommandBusConnector(localSegment, restTemplate, serializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
