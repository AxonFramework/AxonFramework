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

package org.axonframework.messaging.eventhandling.deadletter;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentFactory;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.InstantiatedComponentDefinition;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link ComponentFactory} implementation that creates {@link SequencedDeadLetterQueue} instances for event handling
 * components.
 * <p>
 * This factory is used to create DLQ instances on-demand based on the component name.
 * The factory function receives both the component name and the {@link Configuration} to allow
 * retrieving configuration-dependent factories like those from {@link DeadLetterQueueConfiguration}.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
public class SequencedDeadLetterQueueFactory implements ComponentFactory<SequencedDeadLetterQueue<EventMessage>> {

    private final BiFunction<String, Configuration, SequencedDeadLetterQueue<EventMessage>> factoryFn;

    /**
     * Constructs a factory with a custom factory function that has access to the configuration.
     *
     * @param factoryFn The function that creates a {@link SequencedDeadLetterQueue} for a given name and configuration.
     */
    public SequencedDeadLetterQueueFactory(
            @Nonnull BiFunction<String, Configuration, SequencedDeadLetterQueue<EventMessage>> factoryFn
    ) {
        assertNonNull(factoryFn, "Factory function may not be null");
        this.factoryFn = factoryFn;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public Class<SequencedDeadLetterQueue<EventMessage>> forType() {
        return (Class<SequencedDeadLetterQueue<EventMessage>>) (Class<?>) SequencedDeadLetterQueue.class;
    }

    @Override
    @Nonnull
    public Optional<Component<SequencedDeadLetterQueue<EventMessage>>> construct(
            @Nonnull String name,
            @Nonnull Configuration config
    ) {
        return Optional.of(new InstantiatedComponentDefinition<>(
                new Component.Identifier<>(forType(), name),
                factoryFn.apply(name, config)
        ));
    }

    @Override
    public void registerShutdownHandlers(@Nonnull LifecycleRegistry registry) {
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("type", forType());
    }
}
