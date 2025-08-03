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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SequenceOverridingEventHandlingComponent;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.QualifiedName;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class EventHandlingComponents {

    private final List<EventHandlingComponent> components;

    private EventHandlingComponents(@Nonnull List<EventHandlingComponent> components) {
        this.components = components;
    }

    public static EventHandlingComponents single(@Nonnull Definition.Complete definition) {
        return new EventHandlingComponents(List.of(definition.toComponent()));
    }

    public static EventHandlingComponents single(@Nonnull EventHandlingComponent component) {
        return new EventHandlingComponents(List.of(component));
    }

    public static EventHandlingComponents many(@Nonnull EventHandlingComponent requiredComponent,
                                               @Nonnull EventHandlingComponent... additionalComponents
    ) {
        var components = Stream.concat(
                Stream.of(requiredComponent),
                Stream.of(additionalComponents)
        ).filter(Objects::nonNull).toList();
        return new EventHandlingComponents(components);
    }

    public static EventHandlingComponents many(@Nonnull Definition.Complete requiredDefinition,
                                               @Nonnull Definition.Complete... additionalDefinitions) {
        var components = Stream.concat(
                Stream.of(requiredDefinition.toComponent()),
                Stream.of(additionalDefinitions).map(Definition.Complete::toComponent)
        ).filter(Objects::nonNull).toList();
        return new EventHandlingComponents(components);
    }

    public EventHandlingComponents decorated(@Nonnull UnaryOperator<EventHandlingComponent> decorator) {
        return new EventHandlingComponents(components.stream().map(decorator).toList());
    }

    public List<EventHandlingComponent> toList() {
        return List.copyOf(components);
    }

    public interface Definition {

        public static SequencingPolicyPhase component() {
            return new SimpleDefinition();
        }

        interface SequencingPolicyPhase extends RequiredEventHandlerPhase {

            RequiredEventHandlerPhase sequencingPolicy(@Nonnull SequencingPolicy sequencingPolicy);

            default RequiredEventHandlerPhase sequenceIdentifier(@Nonnull Function<EventMessage<?>, Object> sequencingPolicy){
                return sequencingPolicy(event -> Optional.of(sequencingPolicy.apply(event)));
            }
        }

        interface RequiredEventHandlerPhase {

            AdditionalEventHandlerPhase handles(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler);

            AdditionalEventHandlerPhase handles(@Nonnull Set<QualifiedName> names, @Nonnull EventHandler eventHandler);
        }


        interface AdditionalEventHandlerPhase extends Complete {

            AdditionalEventHandlerPhase handles(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler);

            AdditionalEventHandlerPhase handles(@Nonnull Set<QualifiedName> names, @Nonnull EventHandler eventHandler);
        }

        interface Complete {

            Complete decorated(@Nonnull UnaryOperator<EventHandlingComponent> decorator);

            EventHandlingComponent toComponent();
        }
    }

    static class SimpleDefinition implements Definition.SequencingPolicyPhase,
            Definition.RequiredEventHandlerPhase,
            Definition.AdditionalEventHandlerPhase,
            Definition.Complete {

        private EventHandlingComponent component = new SimpleEventHandlingComponent();

        public SimpleDefinition() {
        }

        @Override
        public Definition.RequiredEventHandlerPhase sequencingPolicy(@Nonnull SequencingPolicy sequencingPolicy) {
            this.component = new SequenceOverridingEventHandlingComponent(sequencingPolicy, component);
            return this;
        }

        @Override
        public Definition.AdditionalEventHandlerPhase handles(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler) {
            component.subscribe(name, eventHandler);
            return this;
        }

        @Override
        public Definition.AdditionalEventHandlerPhase handles(@Nonnull Set<QualifiedName> names, @Nonnull EventHandler eventHandler) {
            component.subscribe(names, eventHandler);
            return this;
        }

        @Override
        public Definition.Complete decorated(@Nonnull UnaryOperator<EventHandlingComponent> decorator) {
            this.component = decorator.apply(component);
            return this;
        }

        @Override
        public EventHandlingComponent toComponent() {
            return component;
        }
    }
}
