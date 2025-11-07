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

package org.axonframework.messaging.monitoring.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.common.TypeReference;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.ComponentDefinition.ComponentCreator;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.LazyInitializedComponentDefinition;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.monitoring.MultiMessageMonitor;
import org.axonframework.messaging.monitoring.NoOpMessageMonitor;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code DefaultMessageMonitorRegistry} is a default implementation of the {@link MessageMonitorRegistry} interface
 * responsible for managing and providing components of {@link MessageMonitor} for various message types such as
 * {@link CommandMessage}, {@link EventMessage}, and {@link QueryMessage}.
 * <p>
 * This class allows registering monitor builders for each message type and resolves those monitors into properly
 * initialized components when required. If no custom monitors are registered for a specific type, default
 * implementations like {@link NoOpMessageMonitor} are returned to ensure no operational interruptions.
 * <p>
 * Internally, it maintains separate lists of {@link ComponentDefinition} to store the registered monitor builders for
 * command, event, and query messages. It also supports registering a generic {@link MessageMonitor} for
 * {@link Message}, in which case a specialized {@link MessageMonitor} is created for each of the supported subtypes.
 *
 * @author Jan Galinski
 * @since 5.0.0
 */
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

    private final List<ComponentDefinition<MessageMonitor<? super CommandMessage>>> commandMonitorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageMonitor<? super EventMessage>>> eventMonitorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageMonitor<? super QueryMessage>>> queryMonitorDefinitions = new ArrayList<>();
    private final List<ComponentDefinition<MessageMonitor<? super SubscriptionQueryUpdateMessage>>> subscriptionQueryUpdateMonitorDefinitions = new ArrayList<>();

    @Nonnull
    @Override
    public MessageMonitorRegistry registerMonitor(@Nonnull ComponentBuilder<MessageMonitor<Message>> monitorBuilder) {
        final var genericMonitorDef = new GenericMonitorDefinition(monitorBuilder);

        registerCommandMonitor(genericMonitorDef::doResolve);
        registerEventMonitor(genericMonitorDef::doResolve);
        registerQueryMonitor(genericMonitorDef::doResolve);
        registerSubscriptionQueryUpdateMonitor(genericMonitorDef::doResolve);

        return this;
    }

    @Nonnull
    @Override
    public MessageMonitorRegistry registerEventMonitor(
            @Nonnull ComponentBuilder<MessageMonitor<? super EventMessage>> monitorBuilder) {
        this.eventMonitorDefinitions.add(ComponentDefinition.ofType(EVENT_MONITOR_TYPE_REF)
                                                            .withBuilder(monitorBuilder)
        );
        return this;
    }

    @Nonnull
    @Override
    public MessageMonitorRegistry registerCommandMonitor(
            @Nonnull ComponentBuilder<MessageMonitor<? super CommandMessage>> monitorBuilder) {
        this.commandMonitorDefinitions.add(ComponentDefinition.ofType(COMMAND_MONITOR_TYPE_REF)
                                                              .withBuilder(monitorBuilder)
        );
        return this;
    }

    @Nonnull
    @Override
    public MessageMonitorRegistry registerQueryMonitor(
            @Nonnull ComponentBuilder<MessageMonitor<? super QueryMessage>> monitorBuilder) {
        this.queryMonitorDefinitions.add(ComponentDefinition.ofType(QUERY_MONITOR_TYPE_REF)
                                                            .withBuilder(monitorBuilder)
        );
        return this;
    }

    @Nonnull
    @Override
    public MessageMonitorRegistry registerSubscriptionQueryUpdateMonitor(
            @Nonnull ComponentBuilder<MessageMonitor<? super SubscriptionQueryUpdateMessage>> monitorBuilder) {
        this.subscriptionQueryUpdateMonitorDefinitions.add(ComponentDefinition.ofType(SUBSCRIPTION_QUERY_UPDATE_MONITOR_TYPE_REF)
                                                                               .withBuilder(monitorBuilder)
        );
        return this;
    }

    @Override
    public MessageMonitor<? super CommandMessage> commandMonitor(final @Nonnull Configuration config) {
        return resolveMonitor(commandMonitorDefinitions, config);
    }

    @Override
    public MessageMonitor<? super EventMessage> eventMonitor(@Nonnull Configuration config) {
        return resolveMonitor(eventMonitorDefinitions, config);
    }

    @Override
    public MessageMonitor<? super QueryMessage> queryMonitor(@Nonnull Configuration config) {
        return resolveMonitor(queryMonitorDefinitions, config);
    }

    @Override
    public MessageMonitor<? super SubscriptionQueryUpdateMessage> subscriptionQueryUpdateMonitor(@Nonnull Configuration config) {
        return resolveMonitor(subscriptionQueryUpdateMonitorDefinitions, config);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("commandMonitors", commandMonitorDefinitions);
        descriptor.describeProperty("eventMonitors", eventMonitorDefinitions);
        descriptor.describeProperty("queryMonitors", queryMonitorDefinitions);
        descriptor.describeProperty("subscriptionQueryUpdateMonitors", subscriptionQueryUpdateMonitorDefinitions);
    }

    // Private class used to lazily resolve the generic Message monitor once and reuse it across registrations.
    private static class GenericMonitorDefinition
            extends LazyInitializedComponentDefinition<MessageMonitor<Message>, MessageMonitor<Message>> {

        GenericMonitorDefinition(@Nonnull ComponentBuilder<MessageMonitor<Message>> builder) {
            super(new Component.Identifier<>(MONITOR_TYPE_REF, null), builder);
        }
    }

    /**
     * Resolves and combines multiple {@link MessageMonitor} components provided by the supplied definitions. If no
     * monitors are resolved, a {@link NoOpMessageMonitor} is returned.
     *
     * @param <T>         the type of the {@link Message} the resulting {@link MessageMonitor} will monitor
     * @param definitions a list of {@link ComponentDefinition} instances for creating {@link MessageMonitor}
     *                    components
     * @param config      the {@link Configuration} to be used for resolving the components
     * @return a single {@link MessageMonitor} instance, which combines multiple monitors into a
     * {@link MultiMessageMonitor}, or a {@link NoOpMessageMonitor} if no monitors are resolved
     * @throws IllegalArgumentException if a provided {@link ComponentDefinition} is of an unsupported type
     */
    private static <T extends Message> MessageMonitor<? super T> resolveMonitor(
            @Nonnull List<ComponentDefinition<MessageMonitor<? super T>>> definitions,
            @Nonnull Configuration config
    ) {
        List<MessageMonitor<? super T>> monitors = new ArrayList<>();
        for (ComponentDefinition<MessageMonitor<? super T>> definition : definitions) {
            if (!(definition instanceof ComponentCreator<MessageMonitor<? super T>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + definition);
            }
            monitors.add(creator.createComponent().resolve(config));
        }

        return !monitors.isEmpty() ? new MultiMessageMonitor<>(monitors) : NoOpMessageMonitor.INSTANCE;
    }
}
