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

import org.axonframework.eventhandling.EventMessage;

/**
 * Contract describing a component that can create {@link SequencedDeadLetterQueue sequenced dead letter queues} for a
 * specific processing group. This queue can be used with event handling and thus should only accept
 * {@link EventMessage event messages}.
 *
 * @param <M> An implementation of {@link EventMessage} contained in the
 *            {@link SequencedDeadLetterQueue sequenced dead letter queues}.
 * @author Gerard Kijs
 * @since 4.8.0
 */
public interface EventProcessingSdlqFactory<M extends EventMessage<?>> {

    /**
     * Creates a new {@link SequencedDeadLetterQueue queue} instance specific for the given {@code processingGroup}.
     *
     * @param processingGroup The processing group of the {@link SequencedDeadLetterQueue}.
     * @return the new {@link SequencedDeadLetterQueue queue}
     */
    SequencedDeadLetterQueue<M> getSdlq(String processingGroup);
}
