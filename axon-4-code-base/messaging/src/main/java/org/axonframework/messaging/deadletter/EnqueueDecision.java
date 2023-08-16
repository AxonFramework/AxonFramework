/*
 * Copyright (c) 2010-2023. Axon Framework
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

import java.util.Optional;

/**
 * A contract towards describing a decision among a {@link DeadLetter dead letter} containing a message of type
 * {@code M}.
 * <p>
 * Describes that the letter should be {@link #shouldEnqueue() enqueued} or not. If the letter should be enqueued the
 * {@link #enqueueCause()} may contain a {@link Throwable}. Furthermore, {@link #withDiagnostics(DeadLetter)} may add
 * {@link DeadLetter#diagnostics() diagnostic} information to the dead letter that should be taken into account when
 * enqueueing the letter.
 * <p>
 * If {@link #shouldEnqueue()} returns {@code false}, that means the dead letter will not be inserted in the queue to
 * begin with, or it will be {@link SequencedDeadLetterQueue#evict(DeadLetter) evicted} from the dead-letter queue.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead letter} that's been made a
 *            decision on.
 * @author Steven van Beelen
 * @see Decisions
 * @since 4.6.0
 */
public interface EnqueueDecision<M extends Message<?>> {

    /**
     * The decision whether the {@link DeadLetter dead letter} should be enqueued in a queue. When {@code false} the
     * dead letter should be evicted.
     *
     * @return {@code true} if the {@link DeadLetter dead letter} should be enqueued, {@code false} if the dead letter
     * should be evicted.
     */
    boolean shouldEnqueue();

    /**
     * A {@link Throwable} {@link Optional} that was part of deciding to enqueue the {@link DeadLetter dead letter} in a
     * queue. Empty if the {@code dead letter} should be evicted or when there is no failure cause used for deciding to
     * enqueue.
     *
     * @return The deciding failure for enqueueing a {@link DeadLetter dead letter}, when present.
     */
    Optional<Throwable> enqueueCause();

    /**
     * Adds {@link DeadLetter#diagnostics()} {@link org.axonframework.messaging.MetaData} to the given {@code letter}.
     * The added diagnostics may provide additional information on the decision that may be used to influence future
     * decisions.
     * <p>
     * By default, the {@code letter} is returned as is.
     *
     * @param letter The {@link DeadLetter dead letter} to add {@link DeadLetter#diagnostics() diagnostic}
     *               {@link org.axonframework.messaging.MetaData} to.
     * @return A copy of the given {@code letter} when {@link DeadLetter#diagnostics() diagnostic}
     * {@link org.axonframework.messaging.MetaData} was added.
     */
    default DeadLetter<? extends M> withDiagnostics(DeadLetter<? extends M> letter) {
        return letter;
    }
}
