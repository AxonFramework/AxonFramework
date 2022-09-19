/*
 * Copyright (c) 2010-2022. Axon Framework
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

/**
 * A functional interface constructing an {@link EnqueueDecision} based on a {@link DeadLetter dead letter} and
 * {@link Throwable cause}. Should be used by components that insert dead letters into and processes dead letters from a
 * {@link SequencedDeadLetterQueue}.
 * <p>
 * Implementers of a policy can use {@link Decisions} to construct the basic types of {@code EnqueueDecision}.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead letter} that will be decided
 *            on through this policy.
 * @author Steven van Beelen
 * @see Decisions
 * @since 4.6.0
 */
@FunctionalInterface
public interface EnqueuePolicy<M extends Message<?>> {

    /**
     * Constructs a {@link EnqueueDecision} based on the given {@code letter} and {@code cause}. This operation is
     * typically invoked when handling a {@link Message} failed and a decision should be made what to do with it.
     * <p>
     * Implementers of this operation can use {@link Decisions} to construct the basic types of
     * {@code EnqueueDecision}.
     *
     * @param letter The {@link DeadLetter dead letter} implementation to make a decision on.
     * @param cause  The {@link Throwable} causing the given {@code letter} to be decided on.
     * @return The decision used to decide what to do with the given {@code letter}.
     */
    EnqueueDecision<M> decide(DeadLetter<? extends M> letter, Throwable cause);
}
