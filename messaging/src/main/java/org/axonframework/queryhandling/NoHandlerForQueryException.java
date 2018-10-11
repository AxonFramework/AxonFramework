/*
 * Copyright (c) 2010-2018. Axon Framework
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
package org.axonframework.queryhandling;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating a query for a single result was executed, but no handlers were found that could provide an
 * answer.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public class NoHandlerForQueryException extends AxonNonTransientException {

    private static final long serialVersionUID = 7525883085990429064L;

    /**
     * Initialize the exception with given {@code message}
     *
     * @param message The message explaining the context of the exception
     */
    public NoHandlerForQueryException(String message) {
        super(message);
    }
}
