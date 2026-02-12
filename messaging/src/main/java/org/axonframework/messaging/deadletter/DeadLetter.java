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

package org.axonframework.messaging.deadletter;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.Metadata;

import java.time.Instant;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Interface describing a dead lettered {@link Message} implementation of generic type {@code M}.
 * <p>
 * The time of storing the {@link #message()} is kept through {@link #enqueuedAt()}. The last time this letter was
 * accessed on either {@link SequencedDeadLetterQueue#requeue(DeadLetter, UnaryOperator)} or
 * {@link SequencedDeadLetterQueue#process(java.util.function.Predicate, java.util.function.Function) processing}, is kept in {@link #lastTouched()}. Additional
 * information on why the letter is enqueued can be found in the {@link #diagnostics() diagnostics}.
 *
 * @param <M> The type of {@link Message} represented by this interface.
 * @author Steven van Beelen
 * @author Allard Buijze
 * @since 4.6.0
 */
public interface DeadLetter<M extends Message> {

    /**
     * The {@link Context.ResourceKey} used whenever a {@link Context} would contain a {@link DeadLetter}.
     */
    Context.ResourceKey<DeadLetter<?>> RESOURCE_KEY = Context.ResourceKey.withLabel("deadLetter");

    /**
     * Returns an {@link Optional} of {DeadLetter}, returning the resource keyed under the
     * {@link #RESOURCE_KEY} in the given {@code context}.
     *
     * @param context the {@link Context} to retrieve the {@code DeadLetter} from, if present
     * @return an {@link Optional} of {DeadLetter}, returning the resource keyed under the
     * {@link #RESOURCE_KEY} in the given {@code context}
     */
    static Optional<DeadLetter<?>> fromContext(@Nonnull Context context) {
        return Optional.ofNullable(context.getResource(RESOURCE_KEY));
    }

    /**
     * The {@link Message} of type {@code M} contained in this letter.
     *
     * @return The {@link Message} of type {@code M} contained in this letter.
     */
    M message();

    Context context();

    /**
     * The {@link Cause cause} for the {@link #message()} to be dead lettered. Can be an {@link Optional#empty()} in
     * case this letter is enqueued without a causal error. For instance, when another letter already present in the
     * queue was blocking it being handled.
     *
     * @return The {@link Cause cause} for the {@link #message()} to be dead lettered.
     */
    Optional<Cause> cause();

    /**
     * The moment in time when the {@link #message()} was entered in a dead letter queue.
     *
     * @return The moment in time when the {@link #message()} was entered in a dead letter queue.
     */
    Instant enqueuedAt();

    /**
     * The moment in time when this letter was last touched. Will equal the {@link #enqueuedAt()} value if this letter
     * is enqueued for the first time.
     *
     * @return The moment in time when this letter was last touched.
     */
    Instant lastTouched();

    /**
     * The diagnostic {@link Metadata} concerning this letter.
     *
     * @return The diagnostic {@link Metadata} concerning this letter.
     */
    Metadata diagnostics();

    /**
     * Construct a copy of this {@link DeadLetter}, replacing the {@link #lastTouched()} with the current time.
     *
     * @return A copy of this {@link DeadLetter} with {@link #lastTouched()} set to now.
     */
    DeadLetter<M> markTouched();

    /**
     * Construct a copy of this {@link DeadLetter}, replacing the {@link #cause()} with the given {@code requeueCause}.
     *
     * @param requeueCause The new cause of the {@link DeadLetter} under construction.
     * @return A copy of this {@link DeadLetter}, replacing the {@link #cause()} with the given {@code requeueCause}.
     */
    DeadLetter<M> withCause(Throwable requeueCause);

    /**
     * Construct a copy of this {@link DeadLetter}, replacing the {@link DeadLetter#diagnostics()} with the given
     * {@code diagnostics}.
     *
     * @param diagnostics The diagnostic {@link Metadata} to append to the {@link DeadLetter} under construction.
     * @return A copy of this {@link DeadLetter}, replacing the {@link DeadLetter#diagnostics()} with the given
     * {@code diagnostics}.
     */
    DeadLetter<M> withDiagnostics(Metadata diagnostics);

    /**
     * Construct a copy of this {@link DeadLetter}, replacing the {@link DeadLetter#diagnostics()} with the result of
     * the given {@code diagnosticsBuilder}. The {@code diagnosticsBuilder} will be invoked with the diagnostics of this
     * instance.
     *
     * @param diagnosticsBuilder The {@link UnaryOperator lambda} constructing diagnostic {@link Metadata} to replace
     *                           the {@code Metadata} of the {@link DeadLetter} under construction.
     * @return A copy of this {@link DeadLetter}, replacing the {@link DeadLetter#diagnostics()} with the result of the
     * given {@code diagnosticsBuilder}.
     */
    default DeadLetter<M> withDiagnostics(UnaryOperator<Metadata> diagnosticsBuilder) {
        return withDiagnostics(diagnosticsBuilder.apply(this.diagnostics()));
    }
}
