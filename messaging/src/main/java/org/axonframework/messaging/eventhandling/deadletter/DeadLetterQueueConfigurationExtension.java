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

package org.axonframework.messaging.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.configuration.ConfigurationExtension;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;

import java.util.function.UnaryOperator;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link ConfigurationExtension} that wraps {@link DeadLetterQueueConfiguration} and exposes a lambda-based API
 * for configuring Dead Letter Queue (DLQ) settings on a
 * {@link PooledStreamingEventProcessorConfiguration}.
 * <p>
 * This extension is designed to be accessed via the
 * {@link PooledStreamingEventProcessorConfiguration#extend(Class)} method:
 * <pre>{@code
 * pooledConfig.extend(DeadLetterQueueConfigurationExtension.class)
 *             .deadLetterQueue(dlq -> dlq.enabled().cacheMaxSize(2048));
 * }</pre>
 * <p>
 * Multiple customizations are composed in order. Each call to {@link #deadLetterQueue(UnaryOperator)}
 * chains the new customization after the previous one, allowing defaults and overrides to merge naturally.
 * <p>
 * The resolved {@link DeadLetterQueueConfiguration} is obtained via the no-arg {@link #deadLetterQueue()} method,
 * which applies all accumulated customizations to a fresh {@link DeadLetterQueueConfiguration} instance on each call.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see DeadLetterQueueConfiguration
 * @see ConfigurationExtension
 */
public class DeadLetterQueueConfigurationExtension
        extends ConfigurationExtension<PooledStreamingEventProcessorConfiguration> {

    private UnaryOperator<DeadLetterQueueConfiguration> customization = UnaryOperator.identity();

    /**
     * Constructs a new {@code DeadLetterQueueConfigurationExtension} for the given parent configuration.
     *
     * @param parent The {@link PooledStreamingEventProcessorConfiguration} this extension belongs to.
     */
    public DeadLetterQueueConfigurationExtension(PooledStreamingEventProcessorConfiguration parent) {
        super(parent);
    }

    /**
     * Configures the Dead Letter Queue using the given customization function.
     * <p>
     * Multiple invocations compose the customizations in order: each new customization is applied
     * after the previous ones, allowing later calls to override specific settings while preserving others.
     *
     * @param customization A function that customizes a {@link DeadLetterQueueConfiguration}.
     * @return This extension instance for fluent chaining.
     */
    public DeadLetterQueueConfigurationExtension deadLetterQueue(
            UnaryOperator<DeadLetterQueueConfiguration> customization
    ) {
        assertNonNull(customization, "DLQ customization may not be null");
        var previous = this.customization;
        this.customization = config -> customization.apply(previous.apply(config));
        return this;
    }

    /**
     * Returns a resolved {@link DeadLetterQueueConfiguration} by applying all accumulated customizations
     * to a fresh instance.
     * <p>
     * Each call creates a new {@link DeadLetterQueueConfiguration}, so modifications to the returned object
     * do not affect subsequent calls.
     *
     * @return A new {@link DeadLetterQueueConfiguration} with all customizations applied.
     */
    public DeadLetterQueueConfiguration deadLetterQueue() {
        return customization.apply(new DeadLetterQueueConfiguration());
    }

    @Override
    public void validate() throws AxonConfigurationException {
        // DLQ configuration is valid even when disabled — no hard requirements.
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        deadLetterQueue().describeTo(descriptor);
    }
}
