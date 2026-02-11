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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link DeadLetterQueueConfiguration}.
 *
 * @author Mateusz Nowak
 */
class DeadLetterQueueConfigurationTest {

    @Nested
    class WhenCreatedWithDefaults {

        @Test
        void isNotEnabled() {
            // given / when
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // then
            assertThat(config.isEnabled()).isFalse();
        }

        @Test
        void hasDefaultEnqueuePolicy() {
            // given / when
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // then
            assertThat(config.enqueuePolicy()).isEqualTo(DeadLetterQueueConfiguration.DEFAULT_ENQUEUE_POLICY);
        }

        @Test
        void hasClearOnResetEnabled() {
            // given / when
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // then
            assertThat(config.clearOnReset()).isTrue();
        }

        @Test
        void hasDefaultCacheMaxSize() {
            // given / when
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // then
            assertThat(config.cacheMaxSize()).isEqualTo(SequenceIdentifierCache.DEFAULT_MAX_SIZE);
        }
    }

    @Nested
    class WhenEnabling {

        @Test
        void enablesConfiguration() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when
            config.enabled();

            // then
            assertThat(config.isEnabled()).isTrue();
        }

        @Test
        void returnsThisForMethodChaining() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when
            DeadLetterQueueConfiguration result = config.enabled();

            // then
            assertThat(result).isSameAs(config);
        }
    }

    @Nested
    class WhenConfiguringEnqueuePolicy {

        @Test
        void setsEnqueuePolicy() {
            // given
            EnqueuePolicy<EventMessage> customPolicy = (letter, cause) -> Decisions.doNotEnqueue();
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when
            config.enqueuePolicy(customPolicy);

            // then
            assertThat(config.enqueuePolicy()).isSameAs(customPolicy);
        }

        @Test
        void rejectsNullEnqueuePolicy() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when / then
            assertThatThrownBy(() -> config.enqueuePolicy(null))
                    .isInstanceOf(AxonConfigurationException.class)
                    .hasMessageContaining("may not be null");
        }
    }

    @Nested
    class WhenConfiguringClearOnReset {

        @Test
        void canDisableClearOnReset() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when
            config.clearOnReset(false);

            // then
            assertThat(config.clearOnReset()).isFalse();
        }

        @Test
        void canEnableClearOnReset() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();
            config.clearOnReset(false);

            // when
            config.clearOnReset(true);

            // then
            assertThat(config.clearOnReset()).isTrue();
        }
    }

    @Nested
    class WhenConfiguringCacheMaxSize {

        @Test
        void setsCacheMaxSize() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when
            config.cacheMaxSize(2048);

            // then
            assertThat(config.cacheMaxSize()).isEqualTo(2048);
        }

        @Test
        void rejectsZeroCacheMaxSize() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when / then
            assertThatThrownBy(() -> config.cacheMaxSize(0))
                    .isInstanceOf(AxonConfigurationException.class);
        }

        @Test
        void rejectsNegativeCacheMaxSize() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when / then
            assertThatThrownBy(() -> config.cacheMaxSize(-1))
                    .isInstanceOf(AxonConfigurationException.class);
        }
    }

    @Nested
    class WhenUsingFluentApi {

        @Test
        void supportsMethodChaining() {
            // given
            EnqueuePolicy<EventMessage> customPolicy = (letter, cause) -> Decisions.doNotEnqueue();

            // when
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration()
                    .enabled()
                    .enqueuePolicy(customPolicy)
                    .clearOnReset(false)
                    .cacheMaxSize(512);

            // then
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.enqueuePolicy()).isSameAs(customPolicy);
            assertThat(config.clearOnReset()).isFalse();
            assertThat(config.cacheMaxSize()).isEqualTo(512);
        }
    }

    @Nested
    class WhenMergingConfigurations {

        @Test
        void laterConfigurationOverridesEarlier() {
            // given
            UnaryOperator<DeadLetterQueueConfiguration> defaults = config -> config
                    .enabled()
                    .clearOnReset(true)
                    .cacheMaxSize(1024);

            UnaryOperator<DeadLetterQueueConfiguration> processorSpecific = config -> config
                    .clearOnReset(false);

            // when
            DeadLetterQueueConfiguration config = processorSpecific.apply(
                    defaults.apply(new DeadLetterQueueConfiguration())
            );

            // then
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.clearOnReset()).isFalse();
            assertThat(config.cacheMaxSize()).isEqualTo(1024); // Not overridden
        }

        @Test
        void partialOverridePreservesDefaults() {
            // given
            EnqueuePolicy<EventMessage> defaultPolicy = (letter, cause) -> Decisions.doNotEnqueue();

            UnaryOperator<DeadLetterQueueConfiguration> defaults = config -> config
                    .enabled()
                    .enqueuePolicy(defaultPolicy)
                    .clearOnReset(true)
                    .cacheMaxSize(2048);

            UnaryOperator<DeadLetterQueueConfiguration> processorSpecific = config -> config
                    .clearOnReset(false); // Only override clearOnReset

            // when
            DeadLetterQueueConfiguration config = processorSpecific.apply(
                    defaults.apply(new DeadLetterQueueConfiguration())
            );

            // then
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.enqueuePolicy()).isSameAs(defaultPolicy);
            assertThat(config.clearOnReset()).isFalse(); // Overridden
            assertThat(config.cacheMaxSize()).isEqualTo(2048);
        }

        @Test
        void chainingMultipleCustomizations() {
            // given
            UnaryOperator<DeadLetterQueueConfiguration> customization1 = DeadLetterQueueConfiguration::enabled;
            UnaryOperator<DeadLetterQueueConfiguration> customization2 = config -> config.clearOnReset(false);
            UnaryOperator<DeadLetterQueueConfiguration> customization3 = config -> config.cacheMaxSize(512);

            // when
            DeadLetterQueueConfiguration config = customization3.apply(
                    customization2.apply(
                            customization1.apply(new DeadLetterQueueConfiguration())
                    )
            );

            // then
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.clearOnReset()).isFalse();
            assertThat(config.cacheMaxSize()).isEqualTo(512);
        }
    }

    @Nested
    class WhenConfiguringFactory {

        @Test
        void hasDefaultFactoryThatCreatesInMemoryQueue() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when
            SequencedDeadLetterQueue<EventMessage> queue = config.factory().apply("test-component");

            // then
            assertThat(queue).isNotNull();
            assertThat(queue).isInstanceOf(InMemorySequencedDeadLetterQueue.class);
        }

        @Test
        void setsCustomFactory() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();
            SequencedDeadLetterQueue<EventMessage> customQueue = InMemorySequencedDeadLetterQueue.defaultQueue();

            // when
            config.factory(name -> customQueue);

            // then
            assertThat(config.factory().apply("any-name")).isSameAs(customQueue);
        }

        @Test
        void rejectsNullFactory() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when / then
            assertThatThrownBy(() -> config.factory(null))
                    .isInstanceOf(AxonConfigurationException.class)
                    .hasMessageContaining("may not be null");
        }

        @Test
        void factoryReceivesComponentName() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();
            AtomicReference<String> capturedName = new AtomicReference<>();
            config.factory(name -> {
                capturedName.set(name);
                return InMemorySequencedDeadLetterQueue.defaultQueue();
            });

            // when
            config.factory().apply("my-processor-dlq");

            // then
            assertThat(capturedName.get()).isEqualTo("my-processor-dlq");
        }
    }

    @Nested
    class WhenDisabling {

        @Test
        void disablesConfiguration() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration().enabled();
            assertThat(config.isEnabled()).isTrue();

            // when
            config.disabled();

            // then
            assertThat(config.isEnabled()).isFalse();
        }

        @Test
        void disableOverridesEnabled() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when
            config.enabled().disabled();

            // then
            assertThat(config.isEnabled()).isFalse();
        }
    }

    @Nested
    class WhenDescribingComponent {

        @Test
        void describeToAddsAllProperties() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration()
                    .enabled()
                    .clearOnReset(false)
                    .cacheMaxSize(2048);
            ComponentDescriptor mockDescriptor = mock(ComponentDescriptor.class);

            // when
            config.describeTo(mockDescriptor);

            // then
            verify(mockDescriptor).describeProperty("enabled", true);
            verify(mockDescriptor).describeProperty("clearOnReset", false);
            verify(mockDescriptor).describeProperty("cacheMaxSize", 2048);
        }
    }
}
