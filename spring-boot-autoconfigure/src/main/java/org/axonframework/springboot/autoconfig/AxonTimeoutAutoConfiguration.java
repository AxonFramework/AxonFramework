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

import org.axonframework.config.LegacyConfiguration;
import org.axonframework.config.LegacyConfigurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.messaging.timeout.HandlerTimeoutHandlerEnhancerDefinition;
import org.axonframework.messaging.timeout.TaskTimeoutSettings;
import org.axonframework.messaging.timeout.UnitOfWorkTimeoutInterceptor;
import org.axonframework.springboot.TimeoutProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Configures the timeout settings for message handlers.
 *
 * @author Mitchell Herrijgers
 * @since 4.11.0
 */
@AutoConfiguration
@EnableConfigurationProperties(value = {
        TimeoutProperties.class
})
@ConditionalOnProperty(prefix = "axon.timeout", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AxonTimeoutAutoConfiguration {

    @Bean
    public HandlerTimeoutHandlerEnhancerDefinition messageTimeoutHandlerEnhancerDefinition(
            TimeoutProperties properties
    ) {
        return new HandlerTimeoutHandlerEnhancerDefinition(
                properties.getHandler().toMessageHandlerTimeoutConfiguration()
        );
    }

    @Bean
    public ConfigurerModule axonTimeoutConfigurerModule(TimeoutProperties properties) {
        return new AxonTimeoutConfigurerModule(properties);
    }

    private static class AxonTimeoutConfigurerModule implements ConfigurerModule {

        private final TimeoutProperties.TransactionTimeoutProperties properties;

        public AxonTimeoutConfigurerModule(TimeoutProperties properties) {
            this.properties = properties.getTransaction();
        }

        @Override
        public void configureModule(@NotNull LegacyConfigurer configurer) {
            configurer.eventProcessing()
                      .registerDefaultHandlerInterceptor((c, name) -> {
                          TaskTimeoutSettings settings = getSettingsForProcessor(name);
                          return new UnitOfWorkTimeoutInterceptor(
                                  "EventProcessor " + name,
                                  settings.getTimeoutMs(),
                                  settings.getWarningThresholdMs(),
                                  settings.getWarningIntervalMs()
                          );
                      });
            // Cannot use the configurer.onInitialize, as it creates a circular creation dependency
            configurer.onStart(Integer.MIN_VALUE, () -> {
                LegacyConfiguration c = configurer.buildConfiguration();
                // TODO #3103 - Revisit this section to adjust it to configurer logic instead of configuration logic.
//                c.commandBus().registerHandlerInterceptor(new UnitOfWorkTimeoutInterceptor(
//                        c.commandBus().getClass().getSimpleName(),
//                        properties.getCommandBus().getTimeoutMs(),
//                        properties.getCommandBus().getWarningThresholdMs(),
//                        properties.getCommandBus().getWarningIntervalMs()
//                ));
                c.queryBus().registerHandlerInterceptor(new UnitOfWorkTimeoutInterceptor(
                        c.queryBus().getClass().getSimpleName(),
                        properties.getQueryBus().getTimeoutMs(),
                        properties.getQueryBus().getWarningThresholdMs(),
                        properties.getQueryBus().getWarningIntervalMs()
                ));
                c.deadlineManager().registerHandlerInterceptor(new UnitOfWorkTimeoutInterceptor(
                        c.deadlineManager().getClass().getSimpleName(),
                        properties.getDeadline().getTimeoutMs(),
                        properties.getDeadline().getWarningThresholdMs(),
                        properties.getDeadline().getWarningIntervalMs()
                ));
            });
        }

        private TaskTimeoutSettings getSettingsForProcessor(String name) {
            return properties.getEventProcessor().getOrDefault(name, properties.getEventProcessors());
        }

        @Override
        public int order() {
            return Integer.MIN_VALUE;
        }
    }
}
