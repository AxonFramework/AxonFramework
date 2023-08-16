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

package org.axonframework.modelling.command;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that the an aggregate could not be found in the repository.
 *
 * @author Allard Buijze
 * @since 0.4
 */
public class AggregateNotFoundException extends AxonNonTransientException {

    private static final long serialVersionUID = 1343530021245649274L;
    private final String aggregateIdentifier;

    /**
     * Initialize a AggregateNotFoundException for an aggregate identifier by given {@code aggregateIdentifier}
     * and given {@code message}.
     *
     * @param aggregateIdentifier The identifier of the aggregate that could not be found
     * @param message             The message describing the cause of the exception
     */
    public AggregateNotFoundException(String aggregateIdentifier, String message) {
        super(message);
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Initialize a AggregateNotFoundException for an aggregate identifier by given {@code aggregateIdentifier}
     * and
     * with the given {@code message} and {@code cause}.
     *
     * @param aggregateIdentifier The identifier of the aggregate that could not be found
     * @param message             The message describing the cause of the exception
     * @param cause               The underlying cause of the exception
     */
    public AggregateNotFoundException(String aggregateIdentifier, String message, Throwable cause) {
        super(message, cause);
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Returns the identifier of the aggregate that could not be found.
     *
     * @return the identifier of the aggregate that could not be found
     */
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}
