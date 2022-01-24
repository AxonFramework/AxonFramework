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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * TODO JavaDoc
 * @author Steven van Beelen
 * @since 4.6.0
 */
public abstract class AbstractDeadLetterEvaluator implements DeadLetterEvaluator {

    protected final ScheduledExecutorService executor;
    private final boolean customExecutorService;

    protected Supplier<Runnable> taskBuilder;

    /**
     *
     * @param builder
     */
    public AbstractDeadLetterEvaluator(Builder<?> builder) {
        builder.validate();
        this.executor = builder.scheduledExecutorService;
        this.customExecutorService = builder.customExecutorService;
    }

    @Override
    public void start(Supplier<Runnable> evaluationTaskBuilder) {
        this.taskBuilder = evaluationTaskBuilder;
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        // If the Executor is customized we can expect the user to shut it down properly.
        return customExecutorService
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.runAsync(executor::shutdown);
    }

    /**
     *
     * @param <B>
     */
    public static class Builder<B extends Builder<?>> {

        private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        private boolean customExecutorService = false;

        /**
         * Sets the {@link ScheduledExecutorService} this {@link DeadLetterEvaluator evaluator} uses to evaluate
         * dead-letters. Defaults to a {@link Executors#newSingleThreadScheduledExecutor()}.
         *
         * @param scheduledExecutorService The scheduled executor used to evaluate dead-letters from a {@link
         *                                 DeadLetterQueue}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public B scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            assertNonNull(scheduledExecutorService, "The ScheduledExecutorService may not be null");
            this.scheduledExecutorService = scheduledExecutorService;
            this.customExecutorService = true;
            //noinspection unchecked
            return (B) this;
        }

        public void validate() {
            // Method kept for overriding
        }
    }
}
