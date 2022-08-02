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

import java.util.function.Function;

/**
 * Exception signalling that the {@link DeadLetter dead-letter} being
 * {@link SequencedDeadLetterQueue#enqueueIfPresent(SequenceIdentifier, Function) enqueued} has a non-existing
 * {@link SequenceIdentifier}.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class MismatchingSequenceIdentifierException extends AxonException {

    private static final long serialVersionUID = 3396791562723986396L;

    /**
     * Constructs an exception based on the given {@code message}.
     *
     * @param message The description of this {@link MismatchingSequenceIdentifierException}.
     */
    public MismatchingSequenceIdentifierException(String message) {
        super(message);
    }
}
