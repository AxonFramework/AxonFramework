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

package org.axonframework.messaging.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.annotation.AnnotatedEventHandlingComponent;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;

import java.util.List;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * Builder interface for configuring collections of {@link EventHandlingComponent} instances.
 * <p>
 * Provides a fluent API for specifying single or multiple components and applying decorations to all components in the
 * collection.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface EventHandlingComponentsConfigurer {

    /**
     * Initial phase for specifying event handling components. At least one component must be configured.
     */
    interface RequiredComponentPhase extends ComponentsPhase {

    }

    /**
     * Additional phase for specifying optional event handling components.
     */
    interface AdditionalComponentPhase extends ComponentsPhase, CompletePhase {

    }

    /**
     * Phase that allows configuring event handling components.
     */
    interface ComponentsPhase {

        /**
         * Configures a single event handling component.
         *
         * @param handlingComponentBuilder The component to configure.
         * @return The complete phase for decoration and finalization.
         */
        @Nonnull
        AdditionalComponentPhase declarative(
                @Nonnull ComponentBuilder<EventHandlingComponent> handlingComponentBuilder
        );

        /**
         * Configures an auto-detected event handling component.
         *
         * @param handlingComponentBuilder The component builder.
         * @return The additional component phase for further configuration.
         */
        @Nonnull
        default AdditionalComponentPhase autodetected(@Nonnull ComponentBuilder<Object> handlingComponentBuilder) {
            requireNonNull(handlingComponentBuilder, "The handling component builder cannot be null.");
            return declarative(c -> new AnnotatedEventHandlingComponent<>(
                    handlingComponentBuilder.build(c),
                    c.getComponent(ParameterResolverFactory.class),
                    ClasspathHandlerDefinition.forClass(c.getClass()),
                    c.getComponent(MessageTypeResolver.class),
                    c.getComponent(EventConverter.class)
            ));
        }
    }

    /**
     * Final phase for applying decorations and building the component list.
     */
    interface CompletePhase {

        /**
         * Applies a decorator to all components in the collection.
         *
         * @param decorator Function to decorate each component.
         * @return This phase for further decoration or finalization.
         */
        @Nonnull
        CompletePhase decorated(
                @Nonnull BiFunction<Configuration, EventHandlingComponent, EventHandlingComponent> decorator
        );

        /**
         * Returns the configured list of event handling components.
         *
         * @return The immutable list of configured components.
         */
        @Nonnull
        List<ComponentBuilder<EventHandlingComponent>> toList();

        /**
         * Builds all configured components using the provided configuration.
         *
         * @param configuration The framework configuration.
         * @return The list of built event handling components.
         */
        @Nonnull
        default List<EventHandlingComponent> build(Configuration configuration) {
            return toList().stream()
                           .map(builder -> builder.build(configuration))
                           .toList();
        }
    }
}

