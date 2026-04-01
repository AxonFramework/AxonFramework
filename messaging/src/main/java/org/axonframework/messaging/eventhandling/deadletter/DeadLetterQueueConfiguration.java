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
import org.axonframework.common.configuration.ConfigurationExtension;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertPositive;
import static org.axonframework.messaging.deadletter.ThrowableCause.truncated;

/**
 * Configuration class holding all settings related to Dead Letter Queue (DLQ) functionality.
 * <p>
 * This class provides a fluent API for configuring the DLQ behavior, including:
 * <ul>
 *     <li>Whether DLQ is enabled</li>
 *     <li>The {@link EnqueuePolicy} to decide which events to dead-letter</li>
 *     <li>Whether to clear the DLQ on processor reset</li>
 *     <li>The maximum size of the sequence identifier cache</li>
 * </ul>
 * <p>
 * The configuration supports natural merging when combined with defaults using the
 * {@code UnaryOperator.andThen()} pattern. Each setter only modifies its specific field,
 * allowing processor-specific configurations to override only selected defaults.
 * <p>
 * Implements {@link ConfigurationExtension} for {@link PooledStreamingEventProcessorConfiguration},
 * so it can be registered directly as a configuration extension:
 * <pre>{@code
 * config.extend(DeadLetterQueueConfiguration.class, dlq -> dlq
 *           .enabled()
 *           .enqueuePolicy((letter, cause) -> Decisions.enqueue(cause))
 *           .clearOnReset(false)
 *           .cacheMaxSize(2048)
 * );
 * }</pre>
 *
 * @author Mateusz Nowak
 * @see EnqueuePolicy
 * @see CachingSequencedDeadLetterQueue
 * @since 5.1.0
 */
public class DeadLetterQueueConfiguration
        implements ConfigurationExtension<PooledStreamingEventProcessorConfiguration> {

    /**
     * The default enqueue policy that always enqueues with a truncated cause message.
     */
    public static final EnqueuePolicy<EventMessage> DEFAULT_ENQUEUE_POLICY =
            (letter, cause) -> Decisions.enqueue(truncated(cause));

    private boolean enabled = false;
    private EnqueuePolicy<EventMessage> enqueuePolicy = DEFAULT_ENQUEUE_POLICY;
    private boolean clearOnReset = true;
    private int cacheMaxSize = SequenceIdentifierCache.DEFAULT_MAX_SIZE;
    private SequencedDeadLetterQueueFactory factory = (processingGroup, configuration) -> InMemorySequencedDeadLetterQueue.defaultQueue();

    /**
     * Creates a new {@code DeadLetterQueueConfiguration} with default settings.
     * <p>
     * By default:
     * <ul>
     *     <li>DLQ is disabled</li>
     *     <li>Enqueue policy always enqueues with truncated cause</li>
     *     <li>Clear on reset is enabled</li>
     *     <li>Cache max size is {@link SequenceIdentifierCache#DEFAULT_MAX_SIZE}</li>
     *     <li>Factory creates {@link InMemorySequencedDeadLetterQueue} instances</li>
     * </ul>
     */
    /**
     * Creates a new {@code DeadLetterQueueConfiguration} for use as a
     * {@link ConfigurationExtension} of the given parent.
     * <p>
     * This constructor is used by the extension mechanism via reflection.
     *
     * @param parent The parent processor configuration.
     */
    public DeadLetterQueueConfiguration(PooledStreamingEventProcessorConfiguration parent) {
        // Defaults set in field initialization; parent is not stored — DLQ config is self-contained
    }

    /**
     * Creates a new {@code DeadLetterQueueConfiguration} with default settings for standalone use.
     */
    public DeadLetterQueueConfiguration() {
        // Defaults set in field initialization
    }

    /**
     * Enables dead-letter queue functionality for this processor.
     * <p>
     * When enabled, failed events will be stored in a dead-letter queue for later processing.
     * The actual queue implementation is provided by a {@link org.axonframework.common.configuration.ComponentFactory}
     * registered with the configuration.
     *
     * @return This configuration instance for fluent chaining.
     * @see #disabled()
     */
    public DeadLetterQueueConfiguration enabled() {
        this.enabled = true;
        return this;
    }

    /**
     * Disables dead-letter queue functionality for this processor.
     * <p>
     * This method is useful when DLQ is enabled by default (e.g., via shared defaults) but needs to be
     * disabled for a specific processor.
     * <p>
     * Example usage:
     * <pre>{@code
     * // Enable DLQ for all processors by default
     * configurer.eventProcessing(ep -> ep.pooledStreaming(ps -> ps
     *     .defaults(d -> d.extend(DeadLetterQueueConfiguration.class, dlq -> dlq.enabled())
     *                     .eventSource(myEventSource))
     *     // But disable for this specific processor
     *     .processor(EventProcessorModule.pooledStreaming("no-dlq-processor")
     *         .eventHandlingComponents(...)
     *         .customized((cfg, c) ->
     *             c.extend(DeadLetterQueueConfiguration.class, dlq -> dlq.disabled())))
     * ));
     * }</pre>
     *
     * @return This configuration instance for fluent chaining.
     * @see #enabled()
     */
    public DeadLetterQueueConfiguration disabled() {
        this.enabled = false;
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
    public DeadLetterQueueConfiguration enqueuePolicy(EnqueuePolicy<EventMessage> enqueuePolicy) {
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
     * Setting this to {@code 0} disables the caching wrapper entirely — the underlying
     * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue} will be used directly.
     * <p>
     * Defaults to {@link SequenceIdentifierCache#DEFAULT_MAX_SIZE} (1024).
     *
     * @param cacheMaxSize The maximum number of non-enqueued identifiers to cache, or {@code 0} to disable caching.
     * @return This configuration instance for fluent chaining.
     */
    public DeadLetterQueueConfiguration cacheMaxSize(int cacheMaxSize) {
        assertPositive(cacheMaxSize, "Cache max size must not be negative");
        this.cacheMaxSize = cacheMaxSize;
        return this;
    }

    /**
     * Sets the {@link SequencedDeadLetterQueueFactory} used to create {@link SequencedDeadLetterQueue} instances.
     * <p>
     * The factory receives a component-scoped processing group identifier that uniquely identifies the dead letter
     * queue within its event processor. A single processor may contain multiple event handling components, each with
     * its own DLQ. The name follows the pattern {@code "DeadLetterQueue[processorName][componentName]"}, for example
     * {@code "DeadLetterQueue[myProcessor][myComponent]"} for a component named {@code "myComponent"} within a
     * processor named {@code "myProcessor"}.
     * <p>
     * This allows using different DLQ implementations such as JPA or JDBC-backed queues
     * instead of the default in-memory implementation.
     * <p>
     * Defaults to a factory that creates {@link InMemorySequencedDeadLetterQueue} instances.
     *
     * @param factory The {@link SequencedDeadLetterQueueFactory} that creates a {@link SequencedDeadLetterQueue} for a given
     *                processing group, e.g. {@code "DeadLetterQueue[myProcessor][0]"}.
     * @return This configuration instance for fluent chaining.
     */
    public DeadLetterQueueConfiguration factory(SequencedDeadLetterQueueFactory factory) {
        assertNonNull(factory, "Factory may not be null");
        this.factory = factory;
        return this;
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
     * Returns the {@link SequencedDeadLetterQueueFactory} used to create {@link SequencedDeadLetterQueue} instances.
     *
     * @return The {@link SequencedDeadLetterQueueFactory}.
     */
    public SequencedDeadLetterQueueFactory factory() {
        return factory;
    }

    /**
     * Checks if the DLQ is enabled.
     *
     * @return {@code true} if DLQ is enabled, {@code false} otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String name() {
        return "deadLetterQueue";
    }

    @Override
    public void validate() throws AxonConfigurationException {
        if (enabled) {
            assertNonNull(factory, "A SequencedDeadLetterQueueFactory is required when DLQ is enabled");
            assertNonNull(enqueuePolicy, "An EnqueuePolicy is required when DLQ is enabled");
        }
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("enabled", enabled);
        descriptor.describeProperty("enqueuePolicy", enqueuePolicy);
        descriptor.describeProperty("clearOnReset", clearOnReset);
        descriptor.describeProperty("cacheMaxSize", cacheMaxSize);
        descriptor.describeProperty("factory", factory);
    }
}
