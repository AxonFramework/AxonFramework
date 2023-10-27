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

package org.axonframework.eventhandling;

import org.axonframework.common.AxonException;

/**
 * Wrapper for exceptions that occurred while calling an @BeforeTransaction or @AfterTransaction annotated method. This
 * might indicate that a transaction could not be committed successfully with a third party.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class TransactionMethodExecutionException extends AxonException {

    private static final long serialVersionUID = 1952095576024390566L;

    /**
     * Initialize the exception with given {@code message} and {@code cause}.
     *
     * @param message Message describing the cause of the exception
     * @param cause   The exception that caused this exception to occur.
     */
    public TransactionMethodExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
