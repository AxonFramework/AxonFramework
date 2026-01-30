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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SequencedDeadLetterQueueFactory}.
 *
 * @author Mateusz Nowak
 */
class SequencedDeadLetterQueueFactoryTest {

    @Nested
    class WhenConstructingFactory {

        @Test
        void constructorAcceptsValidFactoryFunction() {
            // given
            BiFunction<String, Configuration, SequencedDeadLetterQueue<EventMessage>> factoryFn =
                    (name, config) -> InMemorySequencedDeadLetterQueue.defaultQueue();

            // when
            var factory = new SequencedDeadLetterQueueFactory(factoryFn);

            // then
            assertThat(factory).isNotNull();
        }

        @Test
        void constructorRejectsNullFactoryFunction() {
            // given / when / then
            assertThatThrownBy(() -> new SequencedDeadLetterQueueFactory(null))
                    .isInstanceOf(AxonConfigurationException.class)
                    .hasMessageContaining("may not be null");
        }
    }

    @Nested
    class WhenInvokingForType {

        @Test
        void forTypeReturnsCorrectTypeReference() {
            // given
            var factory = new SequencedDeadLetterQueueFactory(
                    (name, config) -> InMemorySequencedDeadLetterQueue.defaultQueue()
            );

            // when
            Class<SequencedDeadLetterQueue<EventMessage>> type = factory.forType();

            // then
            assertThat(type).isNotNull();
            assertThat(SequencedDeadLetterQueue.class).isAssignableFrom(type);
        }
    }

    @Nested
    class WhenConstructingComponent {

        @Test
        void constructInvokesFactoryFunctionWithNameAndConfig() {
            // given
            AtomicReference<String> capturedName = new AtomicReference<>();
            AtomicReference<Configuration> capturedConfig = new AtomicReference<>();

            var factory = new SequencedDeadLetterQueueFactory((name, config) -> {
                capturedName.set(name);
                capturedConfig.set(config);
                return InMemorySequencedDeadLetterQueue.defaultQueue();
            });

            Configuration mockConfig = mock(Configuration.class);
            String componentName = "test-component";

            // when
            factory.construct(componentName, mockConfig);

            // then
            assertThat(capturedName.get()).isEqualTo(componentName);
            assertThat(capturedConfig.get()).isSameAs(mockConfig);
        }

        @Test
        void constructReturnsOptionalWithComponent() {
            // given
            var factory = new SequencedDeadLetterQueueFactory(
                    (name, config) -> InMemorySequencedDeadLetterQueue.defaultQueue()
            );
            Configuration mockConfig = mock(Configuration.class);

            // when
            Optional<Component<SequencedDeadLetterQueue<EventMessage>>> result =
                    factory.construct("test-component", mockConfig);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().resolve(mockConfig)).isInstanceOf(SequencedDeadLetterQueue.class);
        }

        @Test
        void constructReturnsComponentWithCorrectIdentifier() {
            // given
            var factory = new SequencedDeadLetterQueueFactory(
                    (name, config) -> InMemorySequencedDeadLetterQueue.defaultQueue()
            );
            Configuration mockConfig = mock(Configuration.class);
            String componentName = "my-dlq-component";

            // when
            Optional<Component<SequencedDeadLetterQueue<EventMessage>>> result =
                    factory.construct(componentName, mockConfig);

            // then
            assertThat(result).isPresent();
            Component.Identifier<SequencedDeadLetterQueue<EventMessage>> identifier = result.get().identifier();
            assertThat(identifier.name()).isEqualTo(componentName);
            assertThat(identifier.typeAsClass()).isEqualTo(factory.forType());
        }
    }

    @Nested
    class WhenDescribingComponent {

        @Test
        void describeToAddsTypeProperty() {
            // given
            var factory = new SequencedDeadLetterQueueFactory(
                    (name, config) -> InMemorySequencedDeadLetterQueue.defaultQueue()
            );
            ComponentDescriptor mockDescriptor = mock(ComponentDescriptor.class);

            // when
            factory.describeTo(mockDescriptor);

            // then
            verify(mockDescriptor).describeProperty("type", factory.forType());
        }
    }
}
