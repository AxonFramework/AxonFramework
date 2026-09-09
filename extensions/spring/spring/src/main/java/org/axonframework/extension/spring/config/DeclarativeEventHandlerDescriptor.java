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

import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * An {@link EventProcessorDefinition.EventHandlerDescriptor} that brings its own {@link EventHandlingComponent}, rather
 * than a bean whose handlers are uncovered by annotation inspection.
 * <p>
 * A regular descriptor is registered with
 * {@link EventHandlingComponentsConfigurer.ComponentsPhase#autodetected(String, ComponentBuilder) autodetected}, which
 * wraps the bean in an {@code AnnotatedEventHandlingComponent}. That is wrong for a component that already answers
 * {@link EventHandlingComponent#supportedEvents()} itself, and would leave it never invoked. Implementations are
 * registered {@link EventHandlingComponentsConfigurer.ComponentsPhase#declarative(String, ComponentBuilder)
 * declaratively} instead, while keeping every other part of the descriptor contract, so that processor selectors,
 * {@link Namespace} resolution and the {@code axon.eventhandling.processors} settings apply to them unchanged, and so
 * that they can share one processor with plain annotated beans.
 * <p>
 * Package-private on purpose: the two extra questions below exist to carry a legacy Saga, and answering them is not
 * something an application should need to do. It is not part of the public API.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
interface DeclarativeEventHandlerDescriptor extends EventProcessorDefinition.EventHandlerDescriptor {

    /**
     * The builder of the {@link EventHandlingComponent} to register on the processor.
     * <p>
     * Declared next to, rather than instead of, {@link #component()}, because {@link ComponentBuilder} is invariant in
     * its type parameter, which rules out a covariant override.
     *
     * @return the builder of the {@link EventHandlingComponent} to register on the processor
     */
    ComponentBuilder<EventHandlingComponent> handlingComponent();

    /**
     * {@inheritDoc}
     * <p>
     * Defaults to {@link #handlingComponent()}, since an {@code EventHandlingComponent} is the component either way.
     */
    @Override
    default ComponentBuilder<Object> component() {
        return handlingComponent()::build;
    }

    /**
     * The processor name to fall back to when neither a processor selector nor a {@link Namespace} decides where this
     * handler belongs.
     * <p>
     * A plain event handler bean falls back to its package name. A component that is not a bean in its own right has no
     * meaningful package to be grouped by, and a legacy Saga has to keep the name Axon Framework 4 gave it so that its
     * token remains claimable.
     *
     * @return the processor name to prefer over the bean definition's package, if any
     */
    default Optional<String> preferredProcessorName() {
        return Optional.empty();
    }

    /**
     * Configuration defaults to apply to the pooled streaming processor this handler is assigned to, applied only when
     * no {@link EventProcessorDefinition} claims that processor.
     * <p>
     * The condition mirrors Axon Framework 4, which applied its Saga-specific processor defaults only in the absence of
     * a user-provided processor configuration. Defaults are the weakest voice: anything the application states, in
     * properties or in an {@code EventProcessorDefinition}, wins.
     *
     * @return the defaults to apply to the pooled streaming processor this handler is assigned to
     */
    default UnaryOperator<PooledStreamingEventProcessorConfiguration> pooledStreamingDefaults() {
        return UnaryOperator.identity();
    }
}
