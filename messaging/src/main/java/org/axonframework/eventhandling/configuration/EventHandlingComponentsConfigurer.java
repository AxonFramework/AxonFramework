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
import org.axonframework.eventhandling.EventHandlingComponent;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public interface EventHandlingComponentsConfigurer {

    interface ComponentsPhase {

        @Nonnull
        CompletePhase single(@Nonnull Function<EventHandlingComponentBuilder.SequencingPolicyPhase, EventHandlingComponentBuilder.Complete> definition);

        @Nonnull
        default CompletePhase single(@Nonnull EventHandlingComponent component) {
            return many(List.of(component));
        }

        @Nonnull
        default CompletePhase single(
                @Nonnull EventHandlingComponentBuilder.Complete definition
        ) {
            return single(definition.build());
        }

        @SuppressWarnings("unchecked")
        @Nonnull
        CompletePhase many(
                @Nonnull Function<EventHandlingComponentBuilder.SequencingPolicyPhase, EventHandlingComponentBuilder.Complete> requiredComponent,
                @Nonnull Function<EventHandlingComponentBuilder.SequencingPolicyPhase, EventHandlingComponentBuilder.Complete>... additionalComponents
        );

        @Nonnull
        default CompletePhase many(
                @Nonnull EventHandlingComponent requiredComponent,
                @Nonnull EventHandlingComponent... additionalComponents
        ) {
            var components = Stream.concat(
                    Stream.of(requiredComponent),
                    Stream.of(additionalComponents)
            ).filter(Objects::nonNull).toList();
            return many(components);
        }

        @Nonnull
        default CompletePhase many(
                @Nonnull EventHandlingComponentBuilder.Complete requiredComponent,
                @Nonnull EventHandlingComponentBuilder.Complete... additionalComponents
        ) {
            var components = Stream.concat(
                    Stream.of(requiredComponent.build()),
                    Stream.of(additionalComponents).map(EventHandlingComponentBuilder.Complete::build)
            ).filter(Objects::nonNull).toList();
            return many(components);
        }

        CompletePhase many(
                @Nonnull List<EventHandlingComponent> components
        );
    }

    interface CompletePhase {

        CompletePhase decorated(@Nonnull UnaryOperator<EventHandlingComponent> decorator);

        ComponentsPhase and();

        List<EventHandlingComponent> toList();
    }
}

