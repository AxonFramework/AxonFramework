/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.messaging.timeout.HandlerTimeoutConfiguration;
import org.axonframework.messaging.timeout.TimeoutHandlerEnhancerDefinition;
import org.axonframework.springboot.MessageHandlingTimeoutProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Configures the timeout settings for message handlers.
 *
 * @since 4.11
 * @author Mitchell Herrijgers
 */
@AutoConfiguration
@EnableConfigurationProperties(value = {
        MessageHandlingTimeoutProperties.class,
})
public class AxonTimeoutAutoConfiguration {

    @Bean
    public HandlerTimeoutConfiguration messageHandlerTimeoutConfiguration(
            MessageHandlingTimeoutProperties messageHandlingTimeoutProperties) {
        return messageHandlingTimeoutProperties.toMessageHandlerTimeoutConfiguration();
    }

    @Bean
    public TimeoutHandlerEnhancerDefinition messageTimeoutHandlerEnhancerDefinition(
            HandlerTimeoutConfiguration handlerTimeoutConfiguration
    ) {
        return new TimeoutHandlerEnhancerDefinition(handlerTimeoutConfiguration);
    }
}
