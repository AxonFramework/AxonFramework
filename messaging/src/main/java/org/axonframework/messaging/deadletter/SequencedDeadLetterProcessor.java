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

import java.util.function.Predicate;

/**
 * Contract describing a component that can process {@link DeadLetter dead letters} that it has enqueued.
 * <p>
 * Should use the {@link SequencedDeadLetterQueue} for processing as this ensures dead lettered {@link Message Messages}
 * are kept in sequence. Thus processed in order through this component.
 *
 * @param <M> An implementation of {@link Message} contained in the processed {@link DeadLetter dead letters}.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public interface SequencedDeadLetterProcessor<M extends Message> {

    /**
     * Process a sequence of {@link DeadLetter dead letters} matching the given {@code sequenceFilter}.
     * <p>
     * Note that only a <em>single</em> matching sequence is processed! Furthermore, the {@code sequenceFilter} is
     * <em>only</em> invoked for the first letter of a sequence, as the first entry blocks the entire sequence.
     *
     * @param sequenceFilter A filter for the first {@link DeadLetter dead letter} entries of each sequence.
     * @return {@code true} if at least one {@link DeadLetter dead letter} was processed successfully, {@code false}
     * otherwise.
     */
    boolean process(Predicate<DeadLetter<? extends M>> sequenceFilter);

    /**
     * Process any sequence of {@link DeadLetter dead letters} belonging to this component.
     * <p>
     * Note that only a <em>single</em> matching sequence is processed!
     *
     * @return {@code true} if at least one {@link DeadLetter dead letter} was processed successfully, {@code false}
     * otherwise.
     */
    default boolean processAny() {
        return process(letter -> true);
    }
}
