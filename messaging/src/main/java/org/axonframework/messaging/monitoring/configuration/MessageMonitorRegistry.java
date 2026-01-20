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

package org.axonframework.messaging.monitoring.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.messaging.core.configuration.MessagingConfigurationDefaults;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.monitoring.MultiMessageMonitor;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

/**
 * A registry of {@link MessageMonitor MessageMonitors}, acting as a collection of
 * {@link ComponentRegistry#registerComponent(ComponentDefinition) registered
 * MessageMonitor components}.
 * <p>
 * Provides operations to register generic {@link Message}, {@link CommandMessage}-specific,
 * {@link EventMessage}-specific, or {@link QueryMessage}-specific {@code MessageMonitor}s.
 * <p>
 * Multiple {@code MessageMonitor}s will be combined to a single {@link MultiMessageMonitor}.
 * <p>
 * Ingesting and reporting of messages is done via monitoring interception which wrap the monitor.
 * These interception are registered through the {@link MessagingConfigurationDefaults} dispatcher
 * registry factory methods.
 *
 * @author Jan Galinski
 * @since 5.0.0
 */
public interface MessageMonitorRegistry extends DescribableComponent {

    /**
     * Registers a {@link MessageMonitor} for generic {@link Message} types using the supplied monitor builder.
     * The registered monitor will be added to the registry and can be used for monitoring message processing.
     *
     * @param monitorBuilder the {@link ComponentBuilder} responsible for creating the {@link MessageMonitor} instance
     *                       for generic {@link Message} types
     * @return the updated MessageMonitorRegistry instance for fluent configuration
     */
    @Nonnull
    MessageMonitorRegistry registerMonitor(final @Nonnull ComponentBuilder<MessageMonitor<Message>> monitorBuilder);

    /**
     * Registers a {@link MessageMonitor} specific for {@link EventMessage} types using the supplied
     * monitor builder. The registered monitor can be utilized for monitoring event message processing.
     *
     * @param monitorBuilder the {@link ComponentBuilder} responsible for creating the {@link MessageMonitor}
     *                       instance for {@link EventMessage} types
     * @return the updated MessageMonitorRegistry instance, allowing fluent configuration
     */
    @Nonnull
    MessageMonitorRegistry registerEventMonitor(final @Nonnull ComponentBuilder<MessageMonitor<? super EventMessage>> monitorBuilder);

    /**
     * Registers a {@link MessageMonitor} specifically for monitoring the processing of {@link CommandMessage} instances.
     * The provided {@link ComponentBuilder} is responsible for creating the {@link MessageMonitor}.
     *
     * @param monitorBuilder the {@link ComponentBuilder} used to create the {@link MessageMonitor} instance for
     *                       {@link CommandMessage} types.
     * @return the updated MessageMonitorRegistry instance, allowing for a fluent configuration approach.
     */
    @Nonnull
    MessageMonitorRegistry registerCommandMonitor(final @Nonnull ComponentBuilder<MessageMonitor<? super CommandMessage>> monitorBuilder);

    /**
     * Registers a {@link MessageMonitor} specifically for {@link QueryMessage} types using the provided
     * {@link ComponentBuilder}. The registered monitor will be utilized for monitoring query message processing
     * and is added to the registry.
     *
     * @param monitorBuilder the {@link ComponentBuilder} responsible for creating the {@link MessageMonitor}
     *                       for {@link QueryMessage} types
     * @return the updated MessageMonitorRegistry instance, allowing for fluent configuration
     */
    @Nonnull
    MessageMonitorRegistry registerQueryMonitor(final @Nonnull ComponentBuilder<MessageMonitor<? super QueryMessage>> monitorBuilder);

    /**
     * Registers a {@link MessageMonitor} specifically for {@link SubscriptionQueryUpdateMessage} types using the
     * provided {@link ComponentBuilder}. The registered monitor will be utilized for monitoring subscription query
     * update message processing and is added to the registry.
     *
     * @param monitorBuilder the {@link ComponentBuilder} responsible for creating the {@link MessageMonitor}
     *                       for {@link SubscriptionQueryUpdateMessage} types
     * @return the updated MessageMonitorRegistry instance, allowing for fluent configuration
     */
    @Nonnull
    MessageMonitorRegistry registerSubscriptionQueryUpdateMonitor(final @Nonnull ComponentBuilder<MessageMonitor<? super SubscriptionQueryUpdateMessage>> monitorBuilder);

    /**
     * Retrieves a {@link MessageMonitor} dedicated for monitoring {@link CommandMessage} processing.
     *
     * @param config the {@link Configuration} instance used to create the {@link MessageMonitor} instances
     * @return {@link MultiMessageMonitor} composed of all registered {@link MessageMonitor}s, or {@link NoOpMessageMonitor}.
     */
    MessageMonitor<? super CommandMessage> commandMonitor(@Nonnull Configuration config);

    /**
     * Retrieves a {@link MessageMonitor} specifically designed to monitor the processing of {@link EventMessage} instances.
     *
     * @param config the {@link Configuration} instance used to create or retrieve the {@link MessageMonitor} instances
     * @return {@link MultiMessageMonitor} composed of all registered {@link MessageMonitor}s, or {@link NoOpMessageMonitor}.
     */
    MessageMonitor<? super EventMessage> eventMonitor(@Nonnull Configuration config);

    /**
     * Retrieves a {@link MessageMonitor} for monitoring the processing of {@link QueryMessage} instances.
     *
     * @param config the {@link Configuration} used to create or retrieve the {@link MessageMonitor} for {@link QueryMessage} types
     * @return {@link MultiMessageMonitor} composed of all registered {@link MessageMonitor}s, or {@link NoOpMessageMonitor}.
     */
    MessageMonitor<? super QueryMessage> queryMonitor(@Nonnull Configuration config);

    /**
     * Retrieves a {@link MessageMonitor} for monitoring the processing of {@link SubscriptionQueryUpdateMessage} instances.
     *
     * @param config the {@link Configuration} used to create or retrieve the {@link MessageMonitor} for
     *               {@link SubscriptionQueryUpdateMessage} types
     * @return {@link MultiMessageMonitor} composed of all registered {@link MessageMonitor}s, or {@link NoOpMessageMonitor}.
     */
    MessageMonitor<? super SubscriptionQueryUpdateMessage> subscriptionQueryUpdateMonitor(@Nonnull Configuration config);
}
