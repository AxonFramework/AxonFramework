/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.common.AxonException;

/**
 * Exception signaling a {@link SequencedDeadLetterQueue} is overflowing.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetterQueueOverflowException extends AxonException {

    /**
     * Constructs an exception based on the given {@code message}.
     *
     * @param message The description of this {@link DeadLetterQueueOverflowException}.
     */
    public DeadLetterQueueOverflowException(String message) {
        super(message);
    }

    /**
     * Constructs an exception based on the given {@code identifier}.
     *
     * @param identifier The identifier referencing the sequence that has reached its limit.
     */
    public DeadLetterQueueOverflowException(Object identifier) {
        super("Unable to enqueue letter in sequence [" + identifier + "]. "
                      + "The maximum capacity of dead letters has been reached.");
    }
}
