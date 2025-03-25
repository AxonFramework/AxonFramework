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

package org.axonframework.modelling.command;

/**
 * Exception indicating that the (actual) version of a loaded aggregate did not match the given expected version number.
 * This typically means that the aggregate has been modified by another thread between the moment data was queried, and
 * the command modifying the aggregate was handled.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class ConflictingAggregateVersionException extends ConflictingModificationException {

    private final String aggregateIdentifier;
    private final long expectedVersion;
    private final long actualVersion;

    /**
     * Initializes the exception using the given {@code message}.
     *
     * @param aggregateIdentifier The identifier of the aggregate which version was not as expected
     * @param expectedVersion     The version expected by the component loading the aggregate
     * @param actualVersion       The actual version of the aggregate
     */
    public ConflictingAggregateVersionException(String aggregateIdentifier,
                                                long expectedVersion, long actualVersion) {
        super(String.format("The version of aggregate [%s] was not as expected. "
                                    + "Expected [%s], but repository found [%s]",
                            aggregateIdentifier, expectedVersion, actualVersion));
        this.aggregateIdentifier = aggregateIdentifier;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    /**
     * Initializes the exception using the given {@code message} and {@code cause}.
     *
     * @param aggregateIdentifier The identifier of the aggregate which version was not as expected
     * @param expectedVersion     The version expected by the component loading the aggregate
     * @param actualVersion       The actual version of the aggregate
     * @param cause               The underlying cause of the exception
     */
    public ConflictingAggregateVersionException(String aggregateIdentifier,
                                                long expectedVersion, long actualVersion, Throwable cause) {
        super(String.format("The version of aggregate [%s] was not as expected. "
                                    + "Expected [%s], but repository found [%s]",
                            aggregateIdentifier, expectedVersion, actualVersion),
              cause);
        this.aggregateIdentifier = aggregateIdentifier;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    /**
     * Returns the identifier of the aggregate which version is not as expected.
     *
     * @return the identifier of the aggregate which version is not as expected
     */
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * Returns the version expected by the component loading the aggregate.
     *
     * @return the version expected by the component loading the aggregate
     */
    public long getExpectedVersion() {
        return expectedVersion;
    }

    /**
     * Returns the actual version of the aggregate, as loaded by the repository.
     *
     * @return the actual version of the aggregate
     */
    public long getActualVersion() {
        return actualVersion;
    }
}
