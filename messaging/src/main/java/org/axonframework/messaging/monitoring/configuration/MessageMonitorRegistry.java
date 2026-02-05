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
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.configuration.MessagingConfigurationDefaults;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.MultiMessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

/**
 * A registry of {@link MessageMonitor MessageMonitors}, acting as a collection of
 * {@link ComponentRegistry#registerComponent(ComponentDefinition) registered MessageMonitor components}.
 * <p>
 * Provides operations to register generic {@link Message}, {@link CommandMessage}-specific,
 * {@link EventMessage}-specific, or {@link QueryMessage}-specific {@code MessageMonitor}s. Multiple
 * {@code MessageMonitors} will be combined to a single {@link MultiMessageMonitor}.
 * <p>
 * Ingesting and reporting of messages is done via monitoring interception which wrap the monitor. These interception
 * are registered through the {@link MessagingConfigurationDefaults} dispatcher registry factory methods.
 * <p>
 * These operations are expected to be invoked within a
 * {@link org.axonframework.common.configuration.DecoratorDefinition}. As such, <b>any</b> registered monitors are
 * <b>only</b> applied when the infrastructure component requiring them is constructed. When, for example, an
 * {@link org.axonframework.messaging.commandhandling.interception.InterceptingCommandBus} is constructed, this registry
 * is invoked to retrieve monitors. Interceptors that are registered once the {@code InterceptingCommandBus} has already
 * been constructed are not taken into account.
 *
 * @author Jan Galinski
 * @since 5.0.0
 */
@Internal
public interface MessageMonitorRegistry extends DescribableComponent {

    /**
     * Registers a {@link MessageMonitor} for generic {@link Message} types using the supplied monitor builder.
     * <p>
     * Registering a monitor per a {@link ComponentBuilder} ensures the monitor is only built <b>once</b>.
     *
     * @param monitorBuilder the {@link ComponentBuilder} responsible for creating the {@link MessageMonitor} instance
     *                       for generic {@link Message} types
     * @return the updated {@code MessageMonitorRegistry} instance for fluent configuration
     */
    @Nonnull
    MessageMonitorRegistry registerMonitor(
            final @Nonnull ComponentBuilder<MessageMonitor<Message>> monitorBuilder
    );

    /**
     * Registers a component-aware {@link MessageMonitor} for generic {@link Message} types using the given
     * {@code monitorFactory}.
     * <p>
     * The factory will receive the component type and name when the monitor is retrieved allowing for omponent-specific
     * customization of the monitor. Registering a monitor per a {@link MessageMonitorFactory} enforces construction of
     * the monitor for every invocation of the factory, ensuring uniqueness per given type and name. If the monitor will
     * be identical regardless of the given type or name, please use {@link #registerMonitor(ComponentBuilder)}
     * instead.
     *
     * @param monitorFactory the {@link MessageMonitorFactory} responsible for creating the {@link MessageMonitor}
     *                       instance for generic {@link Message} types
     * @return the updated {@code MessageMonitorRegistry} instance for fluent configuration
     */
    @Nonnull
    MessageMonitorRegistry registerMonitor(final @Nonnull MessageMonitorFactory<Message> monitorFactory);

    /**
     * Registers a {@link MessageMonitor} specifically for monitoring the processing of {@link CommandMessage}
     * instances.
     * <p>
     * Registering a monitor per a {@link ComponentBuilder} ensures the monitor is only build <b>once</b>.
     *
     * @param monitorBuilder the {@link ComponentBuilder} used to create the {@link MessageMonitor} instance for
     *                       {@link CommandMessage} types
     * @return the updated {@code MessageMonitorRegistry} instance, allowing for a fluent configuration approach
     */
    @Nonnull
    MessageMonitorRegistry registerCommandMonitor(
            final @Nonnull ComponentBuilder<MessageMonitor<? super CommandMessage>> monitorBuilder
    );

    /**
     * Registers a component-aware {@link MessageMonitor} specifically for monitoring the processing of
     * {@link CommandMessage} instances using the given {@code monitorFactory}.
     * <p>
     * The factory will receive the component type and name when the monitor is retrieved allowing for
     * component-specific customization of the monitor. Registering a monitor per a {@link MessageMonitorFactory}
     * enforces construction of the monitor for every invocation of the factory, ensuring uniqueness per given type and
     * name. If the monitor will be identical regardless of the given type or name, please use
     * {@link #registerCommandMonitor(ComponentBuilder)} instead.
     *
     * @param monitorFactory the {@link MessageMonitorFactory} used to create the {@link MessageMonitor} instance for
     *                       {@link CommandMessage} types
     * @return the updated {@code MessageMonitorRegistry} instance, allowing for a fluent configuration approach
     */
    @Nonnull
    MessageMonitorRegistry registerCommandMonitor(
            final @Nonnull MessageMonitorFactory<? super CommandMessage> monitorFactory
    );

    /**
     * Registers a {@link MessageMonitor} specific for {@link EventMessage} types using the supplied monitor builder.
     * <p>
     * Registering a monitor per a {@link ComponentBuilder} ensures the monitor is only build <b>once</b>.
     *
     * @param monitorBuilder the {@link ComponentBuilder} responsible for creating the {@link MessageMonitor} instance
     *                       for {@link EventMessage} types
     * @return the updated {@code MessageMonitorRegistry} instance, allowing for a fluent configuration approach
     */
    @Nonnull
    MessageMonitorRegistry registerEventMonitor(
            final @Nonnull ComponentBuilder<MessageMonitor<? super EventMessage>> monitorBuilder
    );

    /**
     * Registers a component-aware {@link MessageMonitor} specifically for monitoring the processing of
     * {@link EventMessage} instances using the given {@code monitorFactory}.
     * <p>
     * The factory will receive the component type and name when the monitor is retrieved allowing for
     * component-specific customization of the monitor. Registering a monitor per a {@link MessageMonitorFactory}
     * enforces construction of the monitor for every invocation of the factory, ensuring uniqueness per given type and
     * name. If the monitor will be identical regardless of the given type or name, please use
     * {@link #registerEventMonitor(ComponentBuilder)} instead.
     *
     * @param monitorFactory the {@link MessageMonitorFactory} responsible for creating the {@link MessageMonitor}
     *                       instance for {@link EventMessage} types
     * @return the updated {@code MessageMonitorRegistry} instance, allowing for a fluent configuration approach
     */
    @Nonnull
    MessageMonitorRegistry registerEventMonitor(
            final @Nonnull MessageMonitorFactory<? super EventMessage> monitorFactory
    );

    /**
     * Registers a {@link MessageMonitor} specifically for {@link QueryMessage} types using the provided
     * {@link ComponentBuilder}.
     * <p>
     * Registering a monitor per a {@link ComponentBuilder} ensures the monitor is only build <b>once</b>.
     *
     * @param monitorBuilder the {@link ComponentBuilder} responsible for creating the {@link MessageMonitor} for
     *                       {@link QueryMessage} types
     * @return the updated {@code MessageMonitorRegistry} instance, allowing for a fluent configuration approach
     */
    @Nonnull
    MessageMonitorRegistry registerQueryMonitor(
            final @Nonnull ComponentBuilder<MessageMonitor<? super QueryMessage>> monitorBuilder
    );

    /**
     * Registers a component-aware {@link MessageMonitor} specifically for monitoring the processing of
     * {@link QueryMessage} instances using the given {@code monitorFactory}.
     * <p>
     * The factory will receive the component type and name when the monitor is retrieved allowing for
     * component-specific customization of the monitor. Registering a monitor per a {@link MessageMonitorFactory}
     * enforces construction of the monitor for every invocation of the factory, ensuring uniqueness per given type and
     * name. If the monitor will be identical regardless of the given type or name, please use
     * {@link #registerQueryMonitor(ComponentBuilder)} instead.
     *
     * @param monitorFactory the {@link MessageMonitorFactory} responsible for creating the {@link MessageMonitor} for
     *                       {@link QueryMessage} types
     * @return the updated {@code MessageMonitorRegistry} instance, allowing for a fluent configuration approach
     */
    @Nonnull
    MessageMonitorRegistry registerQueryMonitor(
            final @Nonnull MessageMonitorFactory<? super QueryMessage> monitorFactory);

    /**
     * Registers a {@link MessageMonitor} specifically for {@link SubscriptionQueryUpdateMessage} types using the
     * provided {@link ComponentBuilder}.
     * <p>
     * Registering a monitor per a {@link ComponentBuilder} ensures the monitor is only build <b>once</b>.
     *
     * @param monitorBuilder the {@link ComponentBuilder} responsible for creating the {@link MessageMonitor} for
     *                       {@link SubscriptionQueryUpdateMessage} types
     * @return the updated {@code MessageMonitorRegistry} instance, allowing for a fluent configuration approach
     */
    MessageMonitorRegistry registerSubscriptionQueryUpdateMonitor(
            final @Nonnull ComponentBuilder<MessageMonitor<? super SubscriptionQueryUpdateMessage>> monitorBuilder
    );

    /**
     * Registers a component-aware {@link MessageMonitor} specifically for monitoring the processing of
     * {@link SubscriptionQueryUpdateMessage} instances using the given {@code monitorFactory}.
     * <p>
     * The factory will receive the component type and name when the monitor is retrieved allowing for
     * component-specific customization of the monitor. Registering a monitor per a {@link MessageMonitorFactory}
     * enforces construction of the monitor for every invocation of the factory, ensuring uniqueness per given type and
     * name. If the monitor will be identical regardless of the given type or name, please use
     * {@link #registerSubscriptionQueryUpdateMonitor(ComponentBuilder)} instead.
     *
     * @param monitorFactory the {@link MessageMonitorFactory} responsible for creating the {@link MessageMonitor} for
     *                       {@link SubscriptionQueryUpdateMessage} types
     * @return the updated {@code MessageMonitorRegistry} instance, allowing for a fluent configuration approach
     */
    @Nonnull
    MessageMonitorRegistry registerSubscriptionQueryUpdateMonitor(
            final @Nonnull MessageMonitorFactory<? super SubscriptionQueryUpdateMessage> monitorFactory
    );

    /**
     * Retrieves a {@link MessageMonitor} dedicated for monitoring {@link CommandMessage} processing for a specific
     * {@code componentType} and {@code componentName}.
     * <p>
     * The returned monitor contains generic {@link Message} {@code MessageMonitors} that have been
     * {@link #registerMonitor(MessageMonitorFactory) registered} when the generic builder/factory returns an instance.
     *
     * @param config        the {@link Configuration} instance used to create the {@link MessageMonitor} instances
     * @param componentType the type of the component to retrieve a monitor for
     * @param componentName the name of the component to retrieve a monitor for
     * @return {@link MultiMessageMonitor} composed of all registered {@link MessageMonitor MessageMonitors}, or
     * {@link NoOpMessageMonitor}
     */
    MessageMonitor<? super CommandMessage> commandMonitor(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    );

    /**
     * Retrieves a {@link MessageMonitor} specifically designed to monitor the processing of {@link EventMessage}
     * instances for a specific {@code componentType} and {@code componentName}.
     * <p>
     * The returned monitor contains generic {@link Message} {@code MessageMonitors} that have been
     * {@link #registerMonitor(MessageMonitorFactory) registered} when the generic builder/factory returns an instance.
     *
     * @param config        the {@link Configuration} instance used to create or retrieve the {@link MessageMonitor}
     *                      instances
     * @param componentType the type of the component to retrieve a monitor for
     * @param componentName the name of the component to retrieve a monitor for
     * @return {@link MultiMessageMonitor} composed of all registered {@link MessageMonitor MessageMonitors}, or
     * {@link NoOpMessageMonitor}
     */
    MessageMonitor<? super EventMessage> eventMonitor(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    );

    /**
     * Retrieves a {@link MessageMonitor} for monitoring the processing of {@link QueryMessage} instances for a specific
     * {@code componentType} and {@code componentName}.
     * <p>
     * The returned monitor contains generic {@link Message} {@code MessageMonitors} that have been
     * {@link #registerMonitor(MessageMonitorFactory) registered} when the generic builder/factory returns an instance.
     *
     * @param config        the {@link Configuration} used to create or retrieve the {@link MessageMonitor} for
     *                      {@link QueryMessage} types
     * @param componentType the type of the component to retrieve a monitor for
     * @param componentName the name of the component to retrieve a monitor for
     * @return {@link MultiMessageMonitor} composed of all registered {@link MessageMonitor MessageMonitors}, or
     * {@link NoOpMessageMonitor}
     */
    MessageMonitor<? super QueryMessage> queryMonitor(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    );

    /**
     * Retrieves a {@link MessageMonitor} for monitoring the processing of {@link SubscriptionQueryUpdateMessage}
     * instances for a specific  {@code componentType} and {@code componentName}.
     * <p>
     * The returned monitor contains generic {@link Message} {@code MessageMonitors} that have been
     * {@link #registerMonitor(MessageMonitorFactory) registered} when the generic builder/factory returns an instance.
     *
     * @param config        the {@link Configuration} used to create or retrieve the {@link MessageMonitor} for
     *                      {@link SubscriptionQueryUpdateMessage} types
     * @param componentType the type of the component to retrieve a monitor for
     * @param componentName the name of the component to retrieve a monitor for
     * @return {@link MultiMessageMonitor} composed of all registered {@link MessageMonitor MessageMonitors}, or
     * {@link NoOpMessageMonitor}
     */
    MessageMonitor<? super SubscriptionQueryUpdateMessage> subscriptionQueryUpdateMonitor(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    );
}
