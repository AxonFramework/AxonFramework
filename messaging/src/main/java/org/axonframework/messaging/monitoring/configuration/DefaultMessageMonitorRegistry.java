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

import org.axonframework.common.TypeReference;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.LazyInitializedComponentDefinition;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.MultiMessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code DefaultMessageMonitorRegistry} is a default implementation of the {@link MessageMonitorRegistry} interface
 * responsible for managing and providing components of {@link MessageMonitor} for various message types such as
 * {@link CommandMessage}, {@link EventMessage}, and {@link QueryMessage}.
 * <p>
 * This class allows registering monitor builders and factories for each message type and resolves those monitors into
 * properly initialized components when required. If no custom monitors are registered for a specific type, default
 * implementations like {@link NoOpMessageMonitor} are returned to ensure no operational interruptions.
 * <p>
 * When using the {@link ComponentBuilder} registration methods, this registry will internally maintain a separate lists
 * of {@link ComponentDefinition} to store the registered monitor builders for command, event, and query messages. It
 * also supports registering a generic {@link MessageMonitor} for {@link Message}, in which case a specialized
 * {@link MessageMonitor} is created for each of the supported subtypes.
 *
 * @author Jan Galinski
 * @since 5.0.0
 */
@Internal
public class DefaultMessageMonitorRegistry implements MessageMonitorRegistry {

    private static final TypeReference<MessageMonitor<Message>> MONITOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageMonitor<? super CommandMessage>> COMMAND_MONITOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageMonitor<? super EventMessage>> EVENT_MONITOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageMonitor<? super QueryMessage>> QUERY_MONITOR_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<MessageMonitor<? super SubscriptionQueryUpdateMessage>> SUBSCRIPTION_QUERY_UPDATE_MONITOR_TYPE_REF = new TypeReference<>() {
    };

    private final List<MessageMonitorFactory<? super CommandMessage>> commandMonitorFactories = new ArrayList<>();
    private final List<MessageMonitorFactory<? super EventMessage>> eventMonitorFactories = new ArrayList<>();
    private final List<MessageMonitorFactory<? super QueryMessage>> queryMonitorFactories = new ArrayList<>();
    private final List<MessageMonitorFactory<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateMonitorFactories = new ArrayList<>();

    @Override
    public MessageMonitorRegistry registerMonitor(
            ComponentBuilder<MessageMonitor<Message>> monitorBuilder
    ) {
        final var genericMonitorDef = new GenericMonitorDefinition(monitorBuilder);

        registerCommandMonitor(genericMonitorDef::doResolve);
        registerEventMonitor(genericMonitorDef::doResolve);
        registerQueryMonitor(genericMonitorDef::doResolve);
        registerSubscriptionQueryUpdateMonitor(genericMonitorDef::doResolve);

        return this;
    }

    @Override
    public MessageMonitorRegistry registerMonitor(MessageMonitorFactory<Message> monitorFactory) {
        registerCommandMonitor(monitorFactory);
        registerEventMonitor(monitorFactory);
        registerQueryMonitor(monitorFactory);
        registerSubscriptionQueryUpdateMonitor(monitorFactory);
        return this;
    }

    @Override
    public MessageMonitorRegistry registerCommandMonitor(
            ComponentBuilder<MessageMonitor<? super CommandMessage>> monitorBuilder
    ) {
        ComponentDefinition<MessageMonitor<? super CommandMessage>> monitorDefinition =
                ComponentDefinition.ofType(COMMAND_MONITOR_TYPE_REF).withBuilder(monitorBuilder);
        return registerCommandMonitor(factoryFromDefinition(monitorDefinition));
    }


    @Override
    public MessageMonitorRegistry registerCommandMonitor(
            MessageMonitorFactory<? super CommandMessage> monitorFactory
    ) {
        this.commandMonitorFactories.add(monitorFactory);
        return this;
    }


    @Override
    public MessageMonitorRegistry registerEventMonitor(
            ComponentBuilder<MessageMonitor<? super EventMessage>> monitorBuilder
    ) {
        ComponentDefinition<MessageMonitor<? super EventMessage>> monitorDefinition =
                ComponentDefinition.ofType(EVENT_MONITOR_TYPE_REF).withBuilder(monitorBuilder);
        return registerEventMonitor(factoryFromDefinition(monitorDefinition));
    }


    @Override
    public MessageMonitorRegistry registerEventMonitor(
            MessageMonitorFactory<? super EventMessage> monitorFactory
    ) {
        this.eventMonitorFactories.add(monitorFactory);
        return this;
    }

    @Override
    public MessageMonitorRegistry registerQueryMonitor(
            ComponentBuilder<MessageMonitor<? super QueryMessage>> monitorBuilder
    ) {
        ComponentDefinition<MessageMonitor<? super QueryMessage>> monitorDefinition =
                ComponentDefinition.ofType(QUERY_MONITOR_TYPE_REF).withBuilder(monitorBuilder);
        return registerQueryMonitor(factoryFromDefinition(monitorDefinition));
    }


    @Override
    public MessageMonitorRegistry registerQueryMonitor(
            MessageMonitorFactory<? super QueryMessage> monitorFactory
    ) {
        this.queryMonitorFactories.add(monitorFactory);
        return this;
    }

    @Override
    public MessageMonitorRegistry registerSubscriptionQueryUpdateMonitor(
            ComponentBuilder<MessageMonitor<? super SubscriptionQueryUpdateMessage>> monitorBuilder
    ) {
        ComponentDefinition<MessageMonitor<? super SubscriptionQueryUpdateMessage>> monitorDefinition =
                ComponentDefinition.ofType(SUBSCRIPTION_QUERY_UPDATE_MONITOR_TYPE_REF).withBuilder(monitorBuilder);
        return registerSubscriptionQueryUpdateMonitor(factoryFromDefinition(monitorDefinition));
    }


    @Override
    public MessageMonitorRegistry registerSubscriptionQueryUpdateMonitor(
            MessageMonitorFactory<? super SubscriptionQueryUpdateMessage> monitorFactory
    ) {
        this.subscriptionQueryUpdateMonitorFactories.add(monitorFactory);
        return this;
    }

    @Override
    public MessageMonitor<? super CommandMessage> commandMonitor(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveMonitor(commandMonitorFactories, config, componentType, componentName);
    }

    @Override
    public MessageMonitor<? super EventMessage> eventMonitor(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveMonitor(eventMonitorFactories, config, componentType, componentName);
    }

    @Override
    public MessageMonitor<? super QueryMessage> queryMonitor(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveMonitor(queryMonitorFactories, config, componentType, componentName);
    }

    @Override
    public MessageMonitor<? super SubscriptionQueryUpdateMessage> subscriptionQueryUpdateMonitor(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    ) {
        return resolveMonitor(subscriptionQueryUpdateMonitorFactories, config, componentType, componentName);
    }


    private static <M extends Message> MessageMonitorFactory<M> factoryFromDefinition(
            ComponentDefinition<MessageMonitor<? super M>> monitorDefinition
    ) {
        if (!(monitorDefinition instanceof ComponentDefinition.ComponentCreator<MessageMonitor<? super M>> creator)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported component definition type: " + monitorDefinition);
        }
        return (config, componentType, componentName) -> creator.createComponent().resolve(config);
    }

    /**
     * Resolves and combines multiple {@link MessageMonitor} components provided by the supplied definitions and
     * factories for a specific component type and name. If no monitors are resolved, a {@link NoOpMessageMonitor} is
     * returned.
     *
     * @param <T>           the type of the {@link Message} the resulting {@link MessageMonitor} will monitor
     * @param factories     a list of {@link MessageMonitorFactory} instances for creating component-aware
     *                      {@link MessageMonitor} components
     * @param config        the {@link Configuration} to be used for resolving the components
     * @param componentType the type of the component being monitored
     * @param componentName the name of the component being monitored
     * @return a single {@link MessageMonitor} instance, which combines multiple monitors into a
     * {@link MultiMessageMonitor}, or a {@link NoOpMessageMonitor} if no monitors are resolved
     * @throws IllegalArgumentException if a provided {@link ComponentDefinition} is of an unsupported type
     */
    private static <T extends Message> MessageMonitor<? super T> resolveMonitor(
            List<MessageMonitorFactory<? super T>> factories,
            Configuration config,
            Class<?> componentType,
            String componentName
    ) {
        List<MessageMonitor<? super T>> monitors = new ArrayList<>();
        for (MessageMonitorFactory<? super T> factory : factories) {
            MessageMonitor<? super T> monitor = factory.build(config, componentType, componentName);
            if (monitor != null && !(monitor instanceof NoOpMessageMonitor)) {
                monitors.add(monitor);
            }
        }

        if (monitors.isEmpty()) {
            return NoOpMessageMonitor.INSTANCE;
        } else if (monitors.size() == 1) {
            return monitors.getFirst();
        } else {
            return new MultiMessageMonitor<>(monitors);
        }
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandMonitorFactories", commandMonitorFactories);
        descriptor.describeProperty("eventMonitorFactories", eventMonitorFactories);
        descriptor.describeProperty("queryMonitorFactories", queryMonitorFactories);
        descriptor.describeProperty("subscriptionQueryUpdateMonitorFactories", subscriptionQueryUpdateMonitorFactories);
    }

    // Private class used to lazily resolve the generic Message monitor once and reuse it across registrations.
    private static class GenericMonitorDefinition
            extends LazyInitializedComponentDefinition<MessageMonitor<Message>, MessageMonitor<Message>> {

        GenericMonitorDefinition(ComponentBuilder<MessageMonitor<Message>> builder) {
            super(new Component.Identifier<>(MONITOR_TYPE_REF, null), builder);
        }
    }
}
