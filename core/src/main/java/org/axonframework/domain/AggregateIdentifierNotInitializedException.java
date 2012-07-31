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

package org.axonframework.domain;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that an AggregateRoot instance has failed to provide a valid aggregate identifier in time.
 * Generally, this must be done during the loading or initialization of an aggregate.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AggregateIdentifierNotInitializedException extends AxonNonTransientException {

    private static final long serialVersionUID = -7720267057828643560L;

    /**
     * Initialize the exception with the given <code>message</code>
     *
     * @param message The message describing the exception
     */
    public AggregateIdentifierNotInitializedException(String message) {
        super(message);
    }
}
