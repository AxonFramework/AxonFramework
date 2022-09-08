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

import org.axonframework.common.AxonException;

/**
 * Exception representing that a wrong dead letter was provided to the queue. All
 * {@link org.axonframework.messaging.deadletter.DeadLetter}s supplied back to the
 * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue}, for example the
 * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue#evict(DeadLetter)} method, should be the
 * original supplied by the queue in the first place.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class WrongDeadLetterTypeException extends AxonException {

    /**
     * Constructs a {@code WrongDeadLetterTypeException} with the provided {@code message}.
     *
     * @param message The message containing more details about the cause.
     */
    public WrongDeadLetterTypeException(String message) {
        super(message);
    }
}
