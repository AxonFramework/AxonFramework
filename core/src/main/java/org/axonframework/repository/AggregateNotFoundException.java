/*
 * Copyright (c) 2010. Axon Framework
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

/**
 * Exception indicating that the an aggregate could not be found in the repository.
 *
 * @author Allard Buijze
 * @since 0.4
 */
public class AggregateNotFoundException extends RuntimeException {

    /**
     * Initialize a AggregateNotFoundException with the given <code>message</code>
     *
     * @param message The message describing the cause of the exception
     */
    public AggregateNotFoundException(String message) {
        super(message);
    }

    /**
     * Initialize a AggregateNotFoundException with the given <code>message</code> and <code>cause</code>
     *
     * @param message The message describing the cause of the exception
     * @param cause   The underlying cause of the exception
     */
    public AggregateNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
