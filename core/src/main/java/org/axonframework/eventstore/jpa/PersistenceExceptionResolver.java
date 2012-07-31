/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventstore.jpa;

/**
 * The PersistenceExceptionResolver is used to find out if an exception is caused by  duplicate keys.
 *
 * @author Martin Tilma
 * @since 0.7
 */
public interface PersistenceExceptionResolver {

    /**
     * Indicates whether the given <code>exception</code> represents a duplicate key violation. Typically, duplicate key
     * violations indicates concurrent access to an entity in the application. Two users might be accessing the same
     * Aggregate, for example.
     *
     * @param exception The exception to evaluate
     * @return <code>true</code> if the given exception represents a Duplicate Key Violation, <code>false</code>
     *         otherwise.
     */
    boolean isDuplicateKeyViolation(Exception exception);
}
