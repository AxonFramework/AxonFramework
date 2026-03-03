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

import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;

/**
 * Factory that creates a {@link SequencedDeadLetterQueue} for a given processing group.
 * <p>
 * Implementations are registered as Spring beans and discovered automatically by the
 * {@link DefaultProcessorModuleFactory} when Dead Letter Queue support is enabled for a pooled
 * streaming event processor via the {@code axon.eventhandling.processors.<name>.dlq.enabled}
 * property.
 * <p>
 * The default implementation backed by JPA is registered automatically by
 * {@code JpaDeadLetterQueueAutoConfiguration} when a {@code EntityManagerFactory} bean is present.
 * To use a custom backend, declare a bean of this type with {@code @Bean} — the
 * {@code @ConditionalOnMissingBean} on the default will yield to it.
 * <p>
 * Example:
 * <pre>{@code
 * @Bean
 * public DeadLetterQueueFactory myDlqFactory(MyStorage storage) {
 *     return processingGroup -> new MySequencedDeadLetterQueue(processingGroup, storage);
 * }
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.2
 */
@FunctionalInterface
public interface DeadLetterQueueFactory {

    /**
     * Creates a {@link SequencedDeadLetterQueue} for the given {@code processingGroup}.
     * <p>
     * The {@code processingGroup} is a component-scoped identifier that uniquely identifies the dead letter queue
     * within its event processor. A single processor may contain multiple event handling components, each with its
     * own DLQ. The name follows the pattern {@code "DeadLetterQueue[processorName][componentIndex]"}, for example
     * {@code "DeadLetterQueue[myProcessor][0]"} for the first component of a processor named {@code "myProcessor"}.
     * <p>
     * Implementations should use this value as-is when scoping dead letters in their backing store
     * (e.g., as the {@code processingGroup} column in a database table).
     *
     * @param processingGroup The component-scoped identifier used to scope dead letters to a single event handling
     *                        component's queue, e.g. {@code "DeadLetterQueue[myProcessor][0]"}.
     * @return A {@link SequencedDeadLetterQueue} scoped to the given processing group.
     */
    SequencedDeadLetterQueue<EventMessage> create(String processingGroup);
}
