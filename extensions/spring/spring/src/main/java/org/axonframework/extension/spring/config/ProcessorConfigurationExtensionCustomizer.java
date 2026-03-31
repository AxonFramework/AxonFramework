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

package org.axonframework.extension.spring.config;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;

/**
 * SPI for Spring modules to apply extension-specific settings to an {@link EventProcessorConfiguration}.
 * <p>
 * Implementations of this interface are discovered as Spring beans and invoked during processor module creation.
 * Each customizer receives the Axon {@link Configuration}, the processor name, and the
 * {@link EventProcessorConfiguration} being built, allowing it to call
 * {@link EventProcessorConfiguration#extend(Class)} to register or configure extensions.
 * <p>
 * This mechanism decouples extension-specific auto-configuration (e.g., Dead Letter Queue setup) from the
 * core processor creation logic in {@link DefaultProcessorModuleFactory}. Each extension module provides its
 * own customizer bean, which is applied automatically to every processor.
 * <p>
 * Example — a DLQ customizer that enables dead-lettering based on per-processor properties:
 * <pre>{@code
 * @Bean
 * ProcessorConfigurationExtensionCustomizer dlqCustomizer(DlqProperties properties,
 *                                                         SequencedDeadLetterQueueFactory factory) {
 *     return (axonConfig, processorName, processorConfig) -> {
 *         var dlqProps = properties.forProcessor(processorName);
 *         if (dlqProps.getDlq().isEnabled()) {
 *             processorConfig.extend(DeadLetterQueueConfigurationExtension.class)
 *                            .deadLetterQueue(dlq -> dlq.enabled()
 *                                                       .factory(factory)
 *                                                       .cacheMaxSize(dlqProps.getDlq().getCache().getSize()));
 *         }
 *     };
 * }
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see EventProcessorConfiguration#extend(Class)
 * @see DefaultProcessorModuleFactory
 */
@FunctionalInterface
public interface ProcessorConfigurationExtensionCustomizer {

    /**
     * Customizes the given {@link EventProcessorConfiguration} for the specified processor.
     * <p>
     * Implementations typically use {@link EventProcessorConfiguration#extend(Class)} to register or configure
     * extensions on the processor configuration. The customizer is invoked once per processor during module
     * creation, receiving the fully resolved Axon {@link Configuration} and processor name.
     *
     * @param axonConfig      The Axon {@link Configuration} providing access to registered components.
     * @param processorName   The name of the event processor being configured.
     * @param processorConfig The {@link EventProcessorConfiguration} to customize.
     */
    void customize(Configuration axonConfig, String processorName, EventProcessorConfiguration processorConfig);
}
