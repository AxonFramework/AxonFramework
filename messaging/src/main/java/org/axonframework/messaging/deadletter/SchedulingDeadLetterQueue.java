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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Abstract implementation of a {@link DeadLetterQueue} containing scheduling logic for registered
 * {@link #onAvailable(String, Runnable) availability callbacks}.
 *
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 4.6.0
 */
public abstract class SchedulingDeadLetterQueue<T extends Message<?>> implements DeadLetterQueue<T>, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected final Map<String, Runnable> availabilityCallbacks = new ConcurrentSkipListMap<>();

    protected final Duration expireThreshold;
    protected final ScheduledExecutorService scheduledExecutorService;
    private final boolean customExecutor;

    /**
     * Instantiate a scheduling {@link DeadLetterQueue} implementation based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link SchedulingDeadLetterQueue} implementation with.
     */
    protected SchedulingDeadLetterQueue(Builder<?, T> builder) {
        builder.validate();
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.customExecutor = builder.customExecutor;
        this.expireThreshold = builder.expireThreshold;
    }

    /**
     * Schedule registered {@link #onAvailable(String, Runnable) availability callbacks} matching the given
     * {@code identifier}. Will use the configured {@link Builder#expireThreshold(Duration) expiry threshold} when
     * scheduling.
     *
     * @param identifier The {@link QueueIdentifier} to schedule registered
     *                   {@link #onAvailable(String, Runnable) availability callbacks} for.
     */
    protected void scheduleAvailabilityCallbacks(QueueIdentifier identifier) {
        availabilityCallbacks.entrySet()
                             .stream()
                             .filter(callbackEntry -> callbackEntry.getKey().equals(identifier.group()))
                             .map(Map.Entry::getValue)
                             .forEach(callback -> scheduledExecutorService.schedule(
                                     callback, expireThreshold.toMillis(), TimeUnit.MILLISECONDS
                             ));
    }

    @Override
    public void onAvailable(@Nonnull String group, @Nonnull Runnable callback) {
        if (availabilityCallbacks.put(group, callback) != null) {
            logger.info("Replaced the availability callback for group [{}].", group);
        }
    }

    @Override
    public void registerLifecycleHandlers(LifecycleRegistry lifecycle) {
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS + 1, this::shutdown);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        // When the executor is customized by the user, it's their job to shut it down.
        return customExecutor
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.runAsync(scheduledExecutorService::shutdown);
    }

    /**
     * Abstract builder class to instantiate a {@link SchedulingDeadLetterQueue} implementations.
     * <p>
     * The expiry threshold defaults to a {@link Duration} of 5000 milliseconds, and the
     * {@link ScheduledExecutorService} defaults to a {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)},
     * using an {@link AxonThreadFactory}.
     *
     * @param <B> The type of builder implementing this abstract builder class.
     * @param <T> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
     */
    protected abstract static class Builder<B extends Builder<?, T>, T extends Message<?>> {

        protected Duration expireThreshold = Duration.ofMillis(5000);
        protected ScheduledExecutorService scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor(new AxonThreadFactory("AbstractDeadLetterQueue"));
        protected boolean customExecutor = false;

        /**
         * Sets the threshold used to schedule {@link #onAvailable(String, Runnable) configured availability checks}.
         * Defaults to a {@link Duration} of 5000 milliseconds.
         *
         * @param expireThreshold The threshold for scheduling
         *                        {@link #onAvailable(String, Runnable) availability checks}.
         * @return The current Builder, for fluent interfacing.
         */
        public B expireThreshold(Duration expireThreshold) {
            assertThat(expireThreshold,
                       threshold -> threshold != null && !threshold.isZero() && !threshold.isNegative(),
                       "The expire threshold should be strictly positive");
            this.expireThreshold = expireThreshold;
            //noinspection unchecked
            return (B) this;
        }

        /**
         * Sets the {@link ScheduledExecutorService} this queue uses to invoke
         * {@link #onAvailable(String, Runnable) configured availability callbacks}. Defaults to a
         * {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)}, using an {@link AxonThreadFactory}.
         *
         * @param scheduledExecutorService The {@link ScheduledExecutorService} this queue uses to invoke
         *                                 {@link #onAvailable(String, Runnable) configured availability callbacks}.
         * @return The current Builder, for fluent interfacing.
         */
        public B scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            assertNonNull(scheduledExecutorService, "The ScheduledExecutorService should be non null");
            this.scheduledExecutorService = scheduledExecutorService;
            this.customExecutor = true;
            //noinspection unchecked
            return (B) this;
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            // No assertions required, kept for overriding
        }
    }
}
