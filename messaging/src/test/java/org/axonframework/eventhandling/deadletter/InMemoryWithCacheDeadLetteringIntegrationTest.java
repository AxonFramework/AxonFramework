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

package org.axonframework.eventhandling.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;

/**
 * An implementation of the {@link DeadLetteringEventIntegrationTest} validating the
 * {@link InMemorySequencedDeadLetterQueue} with an {@link org.axonframework.eventhandling.EventProcessor} and
 * {@link DeadLetteringEventHandlerInvoker}.
 *
 * @author Gerard Klijs
 */
class InMemoryWithCacheDeadLetteringIntegrationTest extends DeadLetteringEventIntegrationTest {

    @Override
    protected SequencedDeadLetterQueue<EventMessage<?>> buildDeadLetterQueue() {
        return InMemorySequencedDeadLetterQueue.defaultQueue();
    }

    @Override
    protected boolean identifierCacheEnabled() {
        return true;
    }
}
