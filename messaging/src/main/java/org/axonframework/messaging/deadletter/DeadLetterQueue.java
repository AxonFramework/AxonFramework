/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author Steven van Beelen
 * @since 4.6.0
 */
public interface DeadLetterQueue<T extends Message<?>> {

    /**
     * Add a {@link DeadLetter} to this queue. The {@code deadLetter} will be FIFO ordered with all other dead letters
     * of the same {@link DeadLetter#sequenceIdentifier()}.
     *
     * @param deadLetter the {@link DeadLetter} to add to this queue
     */
    void add(DeadLetter<T> deadLetter);

    /**
     * Adds the given {@code deadLetter} if this queue contains the given {@code deadLetter's} {@link
     * DeadLetter#sequenceIdentifier()}. If there's no queue for the {@code deadLetter} it is ignored.
     *
     * @param deadLetter the {@link DeadLetter} to attached in FIFO ordering for the given {@code sequenceIdentifier}
     * @return {@code true} if the {@code deadLetter} is added, {@code false} otherwise
     */
    default boolean addIfPresent(DeadLetter<T> deadLetter) {
        return addIfPresent(deadLetter.sequenceIdentifier(), () -> deadLetter);
    }

    /**
     * Adds the result of the given {@code deadLetterSupplier} if this queue contains the given {@code
     * sequenceIdentifier}. If there's no queue for the {@code deadLetter} it is ignored.
     *
     * @param sequenceIdentifier the identifier used to validate for contained {@link DeadLetter} instances
     * @param deadLetterSupplier the {@link Supplier} of the {@link DeadLetter}. Only invoked if the given {@code
     *                           sequenceIdentifier} is contained in this queue
     * @return {@code true} if the {@code deadLetter} is added, {@code false} otherwise
     */
    default boolean addIfPresent(String sequenceIdentifier, Supplier<DeadLetter<T>> deadLetterSupplier) {
        boolean canAdd = !isEmpty() && contains(sequenceIdentifier);
        if (canAdd) {
            add(deadLetterSupplier.get());
        }
        return canAdd;
    }

    /**
     * Check whether there's a FIFO ordered queue of {@link DeadLetter} instances with the given {@code
     * sequenceIdentifier}.
     *
     * @param sequenceIdentifier the identifier used to validate for contained {@link DeadLetter} instances
     * @return {@code true} if the identifiers has {@link DeadLetter} instances in this queue, {@code false} otherwise
     */
    boolean contains(String sequenceIdentifier);

    /**
     * Validates whether this queue is empty.
     *
     * @return {@code true} if this queue does not contain any {@link DeadLetter} instances, {@code false} otherwise
     */
    boolean isEmpty();

    /**
     * Peeks the most recently introduced {@link DeadLetter} instances.
     *
     * @return
     */
    Stream<DeadLetter<T>> peek();

    /**
     * @return
     */
    Stream<DeadLetter<T>> poll();
}
