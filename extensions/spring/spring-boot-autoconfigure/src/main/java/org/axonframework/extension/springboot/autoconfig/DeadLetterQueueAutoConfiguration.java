/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.extension.springboot.DeadLetterQueueProcessorProperties;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueConfiguration;
import org.axonframework.messaging.eventhandling.deadletter.SequencedDeadLetterQueueFactory;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot autoconfiguration that provides a
 * {@link PooledStreamingEventProcessorModule.Customization} for Dead Letter Queue support.
 * <p>
 * This configuration is implementation-agnostic — it reads per-processor DLQ settings from
 * {@link DeadLetterQueueProcessorProperties} and configures the {@link DeadLetterQueueConfiguration}
 * extension. The actual queue backend (JPA, JDBC, etc.) is provided by a separate autoconfiguration
 * that registers a {@link SequencedDeadLetterQueueFactory} bean.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see JpaDeadLetterQueueAutoConfiguration
 * @see JdbcDeadLetterQueueAutoConfiguration
 */
@AutoConfiguration(after = {JpaDeadLetterQueueAutoConfiguration.class, JdbcDeadLetterQueueAutoConfiguration.class})
@ConditionalOnBean(SequencedDeadLetterQueueFactory.class)
@EnableConfigurationProperties(DeadLetterQueueProcessorProperties.class)
public class DeadLetterQueueAutoConfiguration {

    /**
     * Creates a {@link PooledStreamingEventProcessorModule.Customization} that enables the Dead Letter Queue
     * extension on each processor where {@code axon.eventhandling.processors.<name>.dlq.enabled=true}.
     *
     * @param properties The DLQ processor properties.
     * @param factory    The {@link SequencedDeadLetterQueueFactory} to use for queue creation.
     * @return A customization that applies DLQ extension settings per processor.
     */
    @Bean
    PooledStreamingEventProcessorModule.Customization dlqCustomization(
            DeadLetterQueueProcessorProperties properties,
            SequencedDeadLetterQueueFactory factory
    ) {
        return (axonConfig, processorConfig) -> {
            var dlqProps = properties.forProcessor(processorConfig.processorName());
            if (dlqProps.getDlq().isEnabled()) {
                return processorConfig.extend(DeadLetterQueueConfiguration.class,
                        () -> new DeadLetterQueueConfiguration().enabled()
                                .factory(factory)
                                .cacheMaxSize(dlqProps.getDlq().getCache().getSize()));
            }
            return processorConfig;
        };
    }
}
