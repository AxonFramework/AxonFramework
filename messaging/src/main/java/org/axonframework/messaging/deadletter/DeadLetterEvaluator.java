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
import java.util.function.Supplier;

// TODO: 24-01-22 the only thing making this dead letter specific is the enqueued method...is that enough?
/**
 * TODO JavaDoc
 * @author Steven van Beelen
 * @since 4.6.0
 */
public interface DeadLetterEvaluator {

    /**
     * TODO: 21-01-22 original intent to construct new tasks, is to potentially allow parallel tasks
     * TODO: 24-01-22 replace the Runnable for an interface?
     * @param evaluationTaskBuilder
     */
    void start(Supplier<Runnable> evaluationTaskBuilder);

    /**
     *
     * @return
     */
    CompletableFuture<Void> shutdown();

    /**
     *
     * @param letter
     */
    void enqueued(DeadLetterEntry<?> letter);
}
