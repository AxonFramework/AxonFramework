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

package org.axonframework.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.SequenceOverridingEventHandlingComponent;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.QualifiedName;

import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Default implementation of the {@link EventHandlingComponentBuilder} providing a fluent API for configuring
 * {@link EventHandlingComponent} instances with event handlers, sequencing policies, and decorators.
 * <p>
 * This builder follows the builder pattern with type-safe phases to ensure proper configuration order:
 * <ol>
 * <li>Optional sequencing policy configuration</li>
 * <li>Required event handler registration (at least one)</li>
 * <li>Optional additional event handlers</li>
 * <li>Optional component decoration</li>
 * <li>Final component building</li>
 * </ol>
 * <p>
 * The builder requires an existing {@link EventHandlingComponent} as its base component.
 * When a {@link org.axonframework.eventhandling.async.SequencingPolicy} is applied, the component is wrapped 
 * in a {@link SequenceOverridingEventHandlingComponent} to provide custom event sequencing behavior.
 * <p>
 * This builder is typically used internally by the framework's configuration system. Users should access
 * it through {@link org.axonframework.configuration.MessagingConfigurer} or similar configuration APIs
 * rather than instantiating it directly.
 * <p>
 * Example usage:
 * <pre>{@code
 * EventHandlingComponent baseComponent = new SimpleEventHandlingComponent();
 * EventHandlingComponent component = new DefaultEventHandlingComponentBuilder(baseComponent)
 *     .sequencingPolicy(event -> Optional.of(event.aggregateIdentifier()))
 *     .handles(new QualifiedName("example", "OrderPlaced"), orderEventHandler)
 *     .handles(new QualifiedName("example", "OrderShipped"), shippingEventHandler)
 *     .decorated(component -> new DecoratingEventHandlingComponent(component))
 *     .build();
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class DefaultEventHandlingComponentBuilder
        implements EventHandlingComponentBuilder.SequencingPolicyPhase,
        EventHandlingComponentBuilder.RequiredEventHandlerPhase,
        EventHandlingComponentBuilder.AdditionalEventHandlerPhase,
        EventHandlingComponentBuilder.Complete {

    private EventHandlingComponent component;

    /**
     * Constructs a builder starting with the given {@link EventHandlingComponent} as the base component.
     * This constructor is used when you want to build upon an existing component implementation.
     *
     * @param component The base {@link EventHandlingComponent} to build upon. Cannot be {@code null}.
     */
    public DefaultEventHandlingComponentBuilder(@Nonnull EventHandlingComponent component) {
        this.component = component;
    }


    @Override
    public EventHandlingComponentBuilder.RequiredEventHandlerPhase sequencingPolicy(
            @Nonnull SequencingPolicy sequencingPolicy
    ) {
        this.component = new SequenceOverridingEventHandlingComponent(sequencingPolicy, component);
        return this;
    }

    @Override
    public EventHandlingComponentBuilder.AdditionalEventHandlerPhase handles(
            @Nonnull QualifiedName name,
            @Nonnull EventHandler eventHandler
    ) {
        component.subscribe(name, eventHandler);
        return this;
    }

    @Override
    public EventHandlingComponentBuilder.AdditionalEventHandlerPhase handles(
            @Nonnull Set<QualifiedName> names,
            @Nonnull EventHandler eventHandler
    ) {
        component.subscribe(names, eventHandler);
        return this;
    }

    @Override
    public EventHandlingComponentBuilder.Complete decorated(
            @Nonnull UnaryOperator<EventHandlingComponent> decorator
    ) {
        this.component = decorator.apply(component);
        return this;
    }

    @Override
    public EventHandlingComponent build() {
        return component;
    }
}
