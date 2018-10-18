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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.grpc.ErrorMessage;
import org.axonframework.axonserver.connector.AxonServerException;

import java.util.List;

/**
 * An Exception which is thrown on a Query Dispatching exception.
 *
 * @author Steven van Beelen
 * @since 4.0
 */
public class QueryDispatchException extends AxonServerException {

    /**
     * Initializes a QueryDispatchException using the given {@code code} and {@code errorMessage}.
     *
     * @param code         a {@link String} specifying the error code received from the Axon Server
     * @param errorMessage the grpc {@link ErrorMessage} describing the error
     */
    public QueryDispatchException(String code, ErrorMessage errorMessage) {
        super(code, errorMessage);
    }

    /**
     * Initializes the QueryDispatchException using the given {@code code} and {@code message}.
     *
     * @param code    a {@link String} specifying the error code received from the Axon Server
     * @param message a {@link String} describing the exception message
     */
    public QueryDispatchException(String code, String message) {
        super(code, message);
    }

    /**
     * Initializes the QueryDispatchException using the given {@code message}, {@code code}, {@code source} and
     * {@code details} .
     *
     * @param message a {@link String} describing the exception message
     * @param code    a {@link String} specifying the error code received from the Axon Server
     * @param source  a {@link String} defining the location that originally reported the error
     * @param details a {@link List} of {@link String}s, each describing a single "cause"
     */
    public QueryDispatchException(String message, String code, String source, List<String> details) {
        super(message, code, source, details);
    }
}
