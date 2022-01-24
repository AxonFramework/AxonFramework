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

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * TODO JavaDoc
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class FixedDelayLetterEvaluator extends AbstractDeadLetterEvaluator {

    private static final int DEFAULT_DELAY = 300;

    private final int initialDelay;
    private final int delay;

    /**
     *
     * @param builder
     */
    public FixedDelayLetterEvaluator(Builder builder) {
        super(builder);
        this.initialDelay = builder.initialDelay;
        this.delay = builder.delay;
    }

    /**
     *
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     *
     * @return
     */
    public static FixedDelayLetterEvaluator defaultEvaluator() {
        return builder().build();
    }

    @Override
    public void start(Supplier<Runnable> evaluationTask) {
        super.start(evaluationTask);
        executor.scheduleWithFixedDelay(taskBuilder.get(), initialDelay, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void enqueued(DeadLetterEntry<?> letter) {
        // Ignored, since evaluation is done in regular intervals
    }

    /**
     *
     */
    public static class Builder extends AbstractDeadLetterEvaluator.Builder<Builder> {

        private int initialDelay = DEFAULT_DELAY;
        private int delay = DEFAULT_DELAY;

        /**
         *
         * @param initialDelay
         * @return
         */
        public Builder initialDelay(int initialDelay) {
            assertStrictPositive(initialDelay, "The initial delay should be strictly positive");
            this.initialDelay = initialDelay;
            return this;
        }

        /**
         *
         * @param delay
         * @return
         */
        public Builder delay(int delay) {
            assertStrictPositive(delay, "The delay should be strictly positive");
            this.delay = delay;
            return this;
        }

        /**
         *
         * @return
         */
        public FixedDelayLetterEvaluator build() {
            return new FixedDelayLetterEvaluator(this);
        }
    }
}
