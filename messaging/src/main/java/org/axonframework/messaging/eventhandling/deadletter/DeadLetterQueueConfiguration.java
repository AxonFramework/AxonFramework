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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertStrictPositive;
import static org.axonframework.messaging.deadletter.ThrowableCause.truncated;

/**
 * Configuration class holding all settings related to Dead Letter Queue (DLQ) functionality.
 * <p>
 * This class provides a fluent API for configuring the DLQ behavior, including:
 * <ul>
 *     <li>The {@link SequencedDeadLetterQueue} to store failed events</li>
 *     <li>The {@link EnqueuePolicy} to decide which events to dead-letter</li>
 *     <li>Whether to clear the DLQ on processor reset</li>
 *     <li>The maximum size of the sequence identifier cache</li>
 * </ul>
 * <p>
 * The configuration supports natural merging when combined with defaults using the
 * {@code UnaryOperator.andThen()} pattern. Each setter only modifies its specific field,
 * allowing processor-specific configurations to override only selected defaults.
 * <p>
 * Example usage:
 * <pre>{@code
 * config.deadLetterQueue(dlq -> dlq
 *     .queue(InMemorySequencedDeadLetterQueue.defaultQueue())
 *     .enqueuePolicy((letter, cause) -> Decisions.enqueue(cause))
 *     .clearOnReset(false)
 *     .cacheMaxSize(2048)
 * )
 * }</pre>
 *
 * @author Mateusz Nowak
 * @see SequencedDeadLetterQueue
 * @see EnqueuePolicy
 * @see CachingSequencedDeadLetterQueue
 * @since 5.0.0
 */
public class DeadLetterQueueConfiguration {

    /**
     * The default enqueue policy that always enqueues with a truncated cause message.
     */
    public static final EnqueuePolicy<EventMessage> DEFAULT_ENQUEUE_POLICY =
            (letter, cause) -> Decisions.enqueue(truncated(cause));

    private SequencedDeadLetterQueue<EventMessage> queue;
    private EnqueuePolicy<EventMessage> enqueuePolicy = DEFAULT_ENQUEUE_POLICY;
    private boolean clearOnReset = true;
    private int cacheMaxSize = SequenceIdentifierCache.DEFAULT_MAX_SIZE;

    /**
     * Creates a new {@code DeadLetterQueueConfiguration} with default settings.
     * <p>
     * By default:
     * <ul>
     *     <li>No queue is configured (DLQ is disabled)</li>
     *     <li>Enqueue policy always enqueues with truncated cause</li>
     *     <li>Clear on reset is enabled</li>
     *     <li>Cache max size is {@link SequenceIdentifierCache#DEFAULT_MAX_SIZE}</li>
     * </ul>
     */
    public DeadLetterQueueConfiguration() {
        // Defaults set in field initialization
    }

    /**
     * Sets the {@link SequencedDeadLetterQueue} to store dead letters in.
     * <p>
     * This is the only required setting to enable DLQ functionality. When set, the processor
     * will automatically wrap event handling components with dead-lettering support.
     *
     * @param queue The {@link SequencedDeadLetterQueue} to store dead letters.
     * @return This configuration instance for fluent chaining.
     */
    public DeadLetterQueueConfiguration queue(@Nonnull SequencedDeadLetterQueue<EventMessage> queue) {
        assertNonNull(queue, "Dead letter queue may not be null");
        this.queue = queue;
        return this;
    }

    /**
     * Sets the {@link EnqueuePolicy} to use when deciding whether to dead-letter a failed event.
     * <p>
     * The policy is invoked for each failed event and can decide whether to enqueue it in the
     * dead-letter queue, and with what diagnostics information.
     * <p>
     * Defaults to a policy that always enqueues with the cause message truncated to 1024 characters.
     *
     * @param enqueuePolicy The {@link EnqueuePolicy} to use.
     * @return This configuration instance for fluent chaining.
     */
    public DeadLetterQueueConfiguration enqueuePolicy(@Nonnull EnqueuePolicy<EventMessage> enqueuePolicy) {
        assertNonNull(enqueuePolicy, "Enqueue policy may not be null");
        this.enqueuePolicy = enqueuePolicy;
        return this;
    }

    /**
     * Sets whether to clear the dead-letter queue when the processor is reset.
     * <p>
     * When {@code true} (the default), the dead-letter queue will be cleared when a processor
     * reset is triggered. This ensures that dead-lettered events from before the reset are not
     * processed again.
     * <p>
     * Set to {@code false} if you want to preserve dead-lettered events across resets.
     *
     * @param clearOnReset Whether to clear the DLQ on reset.
     * @return This configuration instance for fluent chaining.
     */
    public DeadLetterQueueConfiguration clearOnReset(boolean clearOnReset) {
        this.clearOnReset = clearOnReset;
        return this;
    }

    /**
     * Sets the maximum size of the sequence identifier cache used by the
     * {@link CachingSequencedDeadLetterQueue}.
     * <p>
     * The cache stores which sequence identifiers are known to be enqueued or not enqueued,
     * avoiding expensive delegate calls. When the cache exceeds this size, oldest entries
     * are evicted using LRU policy.
     * <p>
     * Defaults to {@link SequenceIdentifierCache#DEFAULT_MAX_SIZE} (1024).
     *
     * @param cacheMaxSize The maximum number of non-enqueued identifiers to cache.
     * @return This configuration instance for fluent chaining.
     */
    public DeadLetterQueueConfiguration cacheMaxSize(int cacheMaxSize) {
        assertStrictPositive(cacheMaxSize, "Cache max size must be greater than zero");
        this.cacheMaxSize = cacheMaxSize;
        return this;
    }

    /**
     * Returns the configured {@link SequencedDeadLetterQueue}, or {@code null} if not configured.
     *
     * @return The dead letter queue, or {@code null}.
     */
    public SequencedDeadLetterQueue<EventMessage> queue() {
        return queue;
    }

    /**
     * Returns the configured {@link EnqueuePolicy}.
     *
     * @return The enqueue policy.
     */
    public EnqueuePolicy<EventMessage> enqueuePolicy() {
        return enqueuePolicy;
    }

    /**
     * Returns whether to clear the dead-letter queue on reset.
     *
     * @return {@code true} if the queue should be cleared on reset, {@code false} otherwise.
     */
    public boolean clearOnReset() {
        return clearOnReset;
    }

    /**
     * Returns the maximum size of the sequence identifier cache.
     *
     * @return The cache max size.
     */
    public int cacheMaxSize() {
        return cacheMaxSize;
    }

    /**
     * Checks if the DLQ is enabled (i.e., a queue has been configured).
     *
     * @return {@code true} if a queue is configured, {@code false} otherwise.
     */
    public boolean isEnabled() {
        return queue != null;
    }
}
