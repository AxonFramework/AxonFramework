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

import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * TODO JavaDoc
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class EnqueueCountLetterEvaluator extends AbstractDeadLetterEvaluator {

    private final int threshold;
    private final AtomicInteger enqueued;

    /**
     * @param builder
     */
    public EnqueueCountLetterEvaluator(Builder builder) {
        super(builder);
        this.threshold = builder.threshold;
        enqueued = new AtomicInteger(0);
    }

    /**
     *
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void enqueued(DeadLetterEntry<?> letter) {
        int enqueueCount = enqueued.incrementAndGet();
        if (enqueueCount >= threshold) {
            executor.submit(taskBuilder.get());
            // TODO: 24-01-22 is this safe?
            enqueued.set(0);
        }
    }

    /**
     *
     */
    public static class Builder extends AbstractDeadLetterEvaluator.Builder<Builder> {

        private int threshold;

        /**
         *
         * @param threshold
         * @return
         */
        public Builder threshold(int threshold) {
            assertStrictPositive(threshold, "The threshold should be strictly positive");
            this.threshold = threshold;
            return this;
        }

        /**
         *
         * @return
         */
        public EnqueueCountLetterEvaluator build() {
            return new EnqueueCountLetterEvaluator(this);
        }
    }
}
