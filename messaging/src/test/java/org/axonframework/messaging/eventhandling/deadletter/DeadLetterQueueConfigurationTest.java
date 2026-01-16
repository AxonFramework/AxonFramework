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
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DeadLetterQueueConfiguration}.
 *
 * @author Mateusz Nowak
 */
class DeadLetterQueueConfigurationTest {

    @Nested
    class WhenCreatedWithDefaults {

        @Test
        void hasNoQueueConfigured() {
            // given / when
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // then
            assertThat(config.queue()).isNull();
        }

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
    class WhenConfiguringQueue {

        @Test
        void setsQueueAndEnablesConfiguration() {
            // given
            SequencedDeadLetterQueue<EventMessage> queue = InMemorySequencedDeadLetterQueue.defaultQueue();
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when
            config.queue(queue);

            // then
            assertThat(config.queue()).isSameAs(queue);
            assertThat(config.isEnabled()).isTrue();
        }

        @Test
        void rejectsNullQueue() {
            // given
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration();

            // when / then
            assertThatThrownBy(() -> config.queue(null))
                    .isInstanceOf(AxonConfigurationException.class)
                    .hasMessageContaining("may not be null");
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
            SequencedDeadLetterQueue<EventMessage> queue = InMemorySequencedDeadLetterQueue.defaultQueue();
            EnqueuePolicy<EventMessage> customPolicy = (letter, cause) -> Decisions.doNotEnqueue();

            // when
            DeadLetterQueueConfiguration config = new DeadLetterQueueConfiguration()
                    .queue(queue)
                    .enqueuePolicy(customPolicy)
                    .clearOnReset(false)
                    .cacheMaxSize(512);

            // then
            assertThat(config.queue()).isSameAs(queue);
            assertThat(config.enqueuePolicy()).isSameAs(customPolicy);
            assertThat(config.clearOnReset()).isFalse();
            assertThat(config.cacheMaxSize()).isEqualTo(512);
            assertThat(config.isEnabled()).isTrue();
        }
    }

    @Nested
    class WhenMergingConfigurations {

        @Test
        void laterConfigurationOverridesEarlier() {
            // given
            SequencedDeadLetterQueue<EventMessage> defaultQueue = InMemorySequencedDeadLetterQueue.defaultQueue();
            SequencedDeadLetterQueue<EventMessage> overrideQueue = InMemorySequencedDeadLetterQueue.defaultQueue();

            UnaryOperator<DeadLetterQueueConfiguration> defaults = config -> config
                    .queue(defaultQueue)
                    .clearOnReset(true)
                    .cacheMaxSize(1024);

            UnaryOperator<DeadLetterQueueConfiguration> processorSpecific = config -> config
                    .queue(overrideQueue)
                    .clearOnReset(false);

            // when
            DeadLetterQueueConfiguration config = processorSpecific.apply(
                    defaults.apply(new DeadLetterQueueConfiguration())
            );

            // then
            assertThat(config.queue()).isSameAs(overrideQueue);
            assertThat(config.clearOnReset()).isFalse();
            assertThat(config.cacheMaxSize()).isEqualTo(1024); // Not overridden
        }

        @Test
        void partialOverridePreservesDefaults() {
            // given
            SequencedDeadLetterQueue<EventMessage> defaultQueue = InMemorySequencedDeadLetterQueue.defaultQueue();
            EnqueuePolicy<EventMessage> defaultPolicy = (letter, cause) -> Decisions.doNotEnqueue();

            UnaryOperator<DeadLetterQueueConfiguration> defaults = config -> config
                    .queue(defaultQueue)
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
            assertThat(config.queue()).isSameAs(defaultQueue);
            assertThat(config.enqueuePolicy()).isSameAs(defaultPolicy);
            assertThat(config.clearOnReset()).isFalse(); // Overridden
            assertThat(config.cacheMaxSize()).isEqualTo(2048);
        }

        @Test
        void chainingMultipleCustomizations() {
            // given
            SequencedDeadLetterQueue<EventMessage> queue = InMemorySequencedDeadLetterQueue.defaultQueue();
            UnaryOperator<DeadLetterQueueConfiguration> customization1 = config -> config.queue(queue);
            UnaryOperator<DeadLetterQueueConfiguration> customization2 = config -> config.clearOnReset(false);
            UnaryOperator<DeadLetterQueueConfiguration> customization3 = config -> config.cacheMaxSize(512);

            // when
            DeadLetterQueueConfiguration config = customization3.apply(
                    customization2.apply(
                            customization1.apply(new DeadLetterQueueConfiguration())
                    )
            );

            // then
            assertThat(config.queue()).isSameAs(queue);
            assertThat(config.clearOnReset()).isFalse();
            assertThat(config.cacheMaxSize()).isEqualTo(512);
        }
    }
}
