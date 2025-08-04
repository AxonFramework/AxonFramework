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
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.QualifiedName;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface EventHandlingComponentConfigurer {

    public static SequencingPolicyPhase component() {
        return new SimpleEventHandlingComponentConfigurer();
    }

    interface SequencingPolicyPhase extends RequiredEventHandlerPhase {

        RequiredEventHandlerPhase sequencingPolicy(@Nonnull SequencingPolicy sequencingPolicy);

        default RequiredEventHandlerPhase sequenceIdentifier(
                @Nonnull Function<EventMessage<?>, Object> sequencingPolicy) {
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
