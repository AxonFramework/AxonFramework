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

/**
 * TODO JavaDoc
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class ManualLetterEvaluator extends AbstractDeadLetterEvaluator {

    /**
     *
     * @param builder
     */
    public ManualLetterEvaluator(Builder builder) {
        super(builder);
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
    public static ManualLetterEvaluator defaultEvaluator() {
        return builder().build();
    }

    @Override
    public void enqueued(DeadLetterEntry<?> letter) {
        // Ignored, since evaluation is done manually only
    }

    /**
     * @return
     */
    public CompletableFuture<Void> evaluate() {
        return CompletableFuture.runAsync(taskBuilder.get(), executor);
    }

    /**
     *
     */
    public static class Builder extends AbstractDeadLetterEvaluator.Builder<Builder> {

        /**
         *
         * @return
         */
        public ManualLetterEvaluator build() {
            return new ManualLetterEvaluator(this);
        }
    }
}
