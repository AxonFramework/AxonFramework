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
package org.axonframework.queryhandling;

import org.axonframework.common.AxonNonTransientException;

import static java.lang.String.format;

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
     * Initialize this exception with the given {@code message}.
     *
     * @param message the message describing the cause of the exception
     */
    public NoHandlerForQueryException(String message) {
        super(message);
    }

    /**
     * Initialize this exception with a message describing the given {@link QueryMessage}. This constructor specifies in
     * its message that missing parameters could be the culprit of finding a matching handler.
     *
     * @param query the {@link QueryMessage query} for which no handler was found
     */
    public NoHandlerForQueryException(QueryMessage<?, ?> query) {
        super(format(
                "No matching handler is available to handle query [%s] with response type [%s]. "
                        + "To find a matching handler, note that the query handler's name should match the query's name, "
                        + "the response, and all the parameters on the query handling method should be resolvable. "
                        + "It is thus recommended to validate the name, response type, and parameters.",
                query.getQueryName(),
                query.getResponseType()
        ));
    }
}
