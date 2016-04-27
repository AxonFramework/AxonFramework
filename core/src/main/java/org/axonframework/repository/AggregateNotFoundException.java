/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.repository;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that the an aggregate could not be found in the repository.
 *
 * @author Allard Buijze
 * @since 0.4
 */
public class AggregateNotFoundException extends AxonNonTransientException {

    private static final long serialVersionUID = 1343530021245649274L;
    private final Class<?> aggregateType;
    private final Object aggregateIdentifier;

    /**
     * Initialize a AggregateNotFoundException for an aggregate identifier by given <code>aggregateIdentifier</code>
     * and given <code>message</code>.
     *
     * @param aggregateIdentifier The identifier of the aggregate that could not be found
     * @param message             The message describing the cause of the exception
     */
    public AggregateNotFoundException(Object aggregateIdentifier, String message) {
        this(null, aggregateIdentifier, message);
    }

    /**
     * Initialize a AggregateNotFoundException for an aggregate given by <code>aggregateType</code> and <code>aggregateIdentifier</code>
     * and given <code>message</code>.
     *
     * @param aggregateType       The type of the aggregate that could not be found
     * @param aggregateIdentifier The identifier of the aggregate that could not be found
     * @param message             The message describing the cause of the exception
     */
    public AggregateNotFoundException(Class<?> aggregateType, Object aggregateIdentifier, String message) {
        super(message);
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Initialize a AggregateNotFoundException for an aggregate identifier by given <code>aggregateIdentifier</code>
     * and
     * with the given <code>message</code> and <code>cause</code>.
     *
     * @param aggregateIdentifier The identifier of the aggregate that could not be found
     * @param message             The message describing the cause of the exception
     * @param cause               The underlying cause of the exception
     */
    public AggregateNotFoundException(Object aggregateIdentifier, String message, Throwable cause) {
        this(null, aggregateIdentifier, message, cause);
    }

    /**
     * Initialize a AggregateNotFoundException for an aggregate given by <code>aggregateType</code> and <code>aggregateIdentifier</code>
     * and
     * with the given <code>message</code> and <code>cause</code>.
     *
     * @param aggregateType       The type of the aggregate that could not be found
     * @param aggregateIdentifier The identifier of the aggregate that could not be found
     * @param message             The message describing the cause of the exception
     * @param cause               The underlying cause of the exception
     */
    public AggregateNotFoundException(Class<?> aggregateType, Object aggregateIdentifier, String message, Throwable cause) {
        super(message, cause);
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Returns the type of the aggregate that could not be found or null if it is unknown.
     *
     * @return the type of the aggregate that could not be found or null if it is unknown
     */
    public Class<?> getAggregateType() {
        return aggregateType;
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
