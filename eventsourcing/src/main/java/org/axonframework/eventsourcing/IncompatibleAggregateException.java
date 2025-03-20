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

package org.axonframework.eventsourcing;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that an aggregate was not compatible with the requirements of the {@link
 * GenericAggregateFactory}.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class IncompatibleAggregateException extends AxonNonTransientException {

    /**
     * Initialize the exception with given {@code message} and {@code cause}.
     *
     * @param message Message describing the reason the aggregate is not compatible
     * @param cause   The cause
     */
    public IncompatibleAggregateException(String message, Exception cause) {
        super(message, cause);
    }

    /**
     * Initialize the exception with given {@code message}.
     *
     * @param message Message describing the reason the aggregate is not compatible
     */
    public IncompatibleAggregateException(String message) {
        super(message);
    }
}
