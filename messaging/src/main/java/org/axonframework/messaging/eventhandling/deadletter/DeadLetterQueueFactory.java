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

import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;

/**
 * Factory that creates a {@link SequencedDeadLetterQueue} for a given processing group.
 * <p>
 * Implementations provide the backing store for dead-lettered events. The factory is invoked once per event handling
 * component when DLQ support is enabled, allowing each component within a processor to have its own queue.
 * <p>
 * Example:
 * <pre>{@code
 * DeadLetterQueueFactory myFactory = processingGroup ->
 *     new MySequencedDeadLetterQueue(processingGroup, storage);
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
     * own DLQ. The name follows the pattern {@code "DeadLetterQueue[processorName][componentName]"}, for example
     * {@code "DeadLetterQueue[myProcessor][myComponent]"} for a component named {@code "myComponent"} within a
     * processor named {@code "myProcessor"}.
     * <p>
     * Implementations should use this value as-is when scoping dead letters in their backing store
     * (e.g., as the {@code processingGroup} column in a database table).
     *
     * @param processingGroup The component-scoped identifier used to scope dead letters to a single event handling
     *                        component's queue, e.g. {@code "DeadLetterQueue[myProcessor][myComponent]"}.
     * @return A {@link SequencedDeadLetterQueue} scoped to the given processing group.
     */
    SequencedDeadLetterQueue<EventMessage> create(String processingGroup);
}
