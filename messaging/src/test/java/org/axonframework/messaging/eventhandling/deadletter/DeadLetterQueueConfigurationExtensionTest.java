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
import org.axonframework.messaging.eventhandling.configuration.EventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessorConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DeadLetterQueueConfigurationExtension}.
 *
 * @author Mateusz Nowak
 */
class DeadLetterQueueConfigurationExtensionTest {

    private PooledStreamingEventProcessorConfiguration createParent() {
        return new PooledStreamingEventProcessorConfiguration(
                new EventProcessorConfiguration("test-processor", null)
        );
    }

    @Nested
    class WhenCreated {

        @Test
        void hasDisabledDlqByDefault() {
            // given
            PooledStreamingEventProcessorConfiguration parent = createParent();

            // when
            DeadLetterQueueConfigurationExtension extension =
                    new DeadLetterQueueConfigurationExtension(parent);

            // then
            assertThat(extension.deadLetterQueue().isEnabled()).isFalse();
        }

        @Test
        void canBeAccessedFromParentViaExtend() {
            // given
            PooledStreamingEventProcessorConfiguration parent = createParent();

            // when
            DeadLetterQueueConfigurationExtension extension =
                    parent.extend(DeadLetterQueueConfigurationExtension.class);

            // then
            assertThat(extension).isNotNull();
        }
    }

    @Nested
    class WhenCustomizing {

        @Test
        void enablesDlqViaLambda() {
            // given
            PooledStreamingEventProcessorConfiguration parent = createParent();
            DeadLetterQueueConfigurationExtension extension =
                    new DeadLetterQueueConfigurationExtension(parent);

            // when
            extension.deadLetterQueue(dlq -> dlq.enabled());

            // then
            assertThat(extension.deadLetterQueue().isEnabled()).isTrue();
        }

        @Test
        void composesMultipleCustomizations() {
            // given
            PooledStreamingEventProcessorConfiguration parent = createParent();
            DeadLetterQueueConfigurationExtension extension =
                    new DeadLetterQueueConfigurationExtension(parent);

            // when
            extension.deadLetterQueue(dlq -> dlq.enabled().cacheMaxSize(2048));
            extension.deadLetterQueue(dlq -> dlq.clearOnReset(false));

            // then
            DeadLetterQueueConfiguration resolved = extension.deadLetterQueue();
            assertThat(resolved.isEnabled()).isTrue();
            assertThat(resolved.cacheMaxSize()).isEqualTo(2048);
            assertThat(resolved.clearOnReset()).isFalse();
        }

        @Test
        void returnsFreshInstanceOnEachResolve() {
            // given
            PooledStreamingEventProcessorConfiguration parent = createParent();
            DeadLetterQueueConfigurationExtension extension =
                    new DeadLetterQueueConfigurationExtension(parent);
            extension.deadLetterQueue(dlq -> dlq.enabled());

            // when
            DeadLetterQueueConfiguration first = extension.deadLetterQueue();
            DeadLetterQueueConfiguration second = extension.deadLetterQueue();

            // then
            assertThat(first).isNotSameAs(second);
        }

        @Test
        void supportsFluentChaining() {
            // given
            PooledStreamingEventProcessorConfiguration parent = createParent();
            DeadLetterQueueConfigurationExtension extension =
                    new DeadLetterQueueConfigurationExtension(parent);

            // when
            DeadLetterQueueConfigurationExtension result = extension
                    .deadLetterQueue(dlq -> dlq.enabled())
                    .deadLetterQueue(dlq -> dlq.cacheMaxSize(512));

            // then
            assertThat(result).isSameAs(extension);
            assertThat(result.deadLetterQueue().isEnabled()).isTrue();
            assertThat(result.deadLetterQueue().cacheMaxSize()).isEqualTo(512);
        }
    }

    @Nested
    class WhenAccessedViaExtend {

        @Test
        void canBeAccessedFromPooledConfigViaExtend() {
            // given
            PooledStreamingEventProcessorConfiguration pooledConfig = createParent();

            // when
            DeadLetterQueueConfigurationExtension extension =
                    pooledConfig.extend(DeadLetterQueueConfigurationExtension.class);

            // then
            assertThat(extension).isNotNull();
            assertThat(extension.deadLetterQueue().isEnabled()).isFalse();
        }

        @Test
        void returnsSameInstanceOnSubsequentExtendCalls() {
            // given
            PooledStreamingEventProcessorConfiguration pooledConfig = createParent();

            // when
            DeadLetterQueueConfigurationExtension first =
                    pooledConfig.extend(DeadLetterQueueConfigurationExtension.class);
            DeadLetterQueueConfigurationExtension second =
                    pooledConfig.extend(DeadLetterQueueConfigurationExtension.class);

            // then
            assertThat(first).isSameAs(second);
        }

        @Test
        void failsOnIncompatibleParent() {
            // given
            SubscribingEventProcessorConfiguration subscribingConfig =
                    new SubscribingEventProcessorConfiguration(
                            new EventProcessorConfiguration("test-processor", null)
                    );

            // when / then
            assertThatThrownBy(
                    () -> subscribingConfig.extend(DeadLetterQueueConfigurationExtension.class)
            ).isInstanceOf(AxonConfigurationException.class);
        }
    }
}
