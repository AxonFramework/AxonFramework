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

package org.axonframework.eventhandling.tokenstore;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating that a processor tried to claim a Token (either by retrieving or updating it) that has already
 * been claimed by another process. This typically happens when two processes (JVM's) contain processors with the same
 * name (and potentially the same configuration). In such case, only the first processor can use the TrackingToken.
 * <p>
 * Processes may retry obtaining the claim, preferably after a brief waiting period.
 */
public class UnableToClaimTokenException extends AxonTransientException {

    /**
     * Initialize the exception with given {@code message}.
     *
     * @param message The message describing the exception
     */
    public UnableToClaimTokenException(String message) {
        super(message);
    }

    /**
     * Initialize the exception with given {@code message} and {@code cause}.
     *
     * @param message The message describing the exception
     * @param cause   The cause of the failure
     */
    public UnableToClaimTokenException(String message, Throwable cause) {
        super(message, cause);
    }

}
