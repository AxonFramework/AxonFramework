/*
 * Copyright (c) 2010-2026. Axon Framework
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
package org.axonframework.messaging.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonException;

import static java.lang.String.format;

/**
 * Exception indicating this instance has no {@link QueryHandler} for a given {@link QueryMessage}.
 *
 * @author Marc Gathier
 * @since 3.1.0
 */
public class NoHandlerForQueryException extends AxonException {

    /**
     * Constructs a {@code NoHandlerForQueryException} with a message describing the given {@link QueryMessage},
     * specific for {@link QueryBus QueryBuses}.
     *
     * @param query The {@link QueryMessage query} for which no handler was found.
     * @return A {@code NoHandlerForQueryException} with a message describing the given {@link QueryMessage}, specific
     * for {@link QueryBus QueryBuses}.
     */
    public static NoHandlerForQueryException forBus(@Nonnull QueryMessage query) {
        return new NoHandlerForQueryException(format(
                "No matching handler is available to handle query of type [%s]. "
                        + "To find a matching handler, note that the query handler's name should match the query's name.",
                query.type()
        ));
    }

    /**
     * Constructs a {@code NoHandlerForQueryException} with a message describing the given {@link QueryMessage},
     * specific for {@link QueryHandlingComponent QueryHandlingComponents}.
     * <p>
     * This factory method specifies in its message that missing parameters could be the culprit of finding a matching
     * handler.
     *
     * @param query The {@link QueryMessage query} for which no handler was found.
     * @return A {@code NoHandlerForQueryException} with a message describing the given {@link QueryMessage}, specific
     * for {@link QueryHandlingComponent QueryHandlingComponents}.
     */
    public static NoHandlerForQueryException forHandlingComponent(@Nonnull QueryMessage query) {
        return new NoHandlerForQueryException(format(
                "No matching handler is available to handle query of type [%s]. "
                        + "To find a matching handler, note that the query handler's name should match the query's name, "
                        + "and all the parameters on the query handling method should be resolvable. "
                        + "It is thus recommended to validate the name, and parameters.",
                query.type()
        ));
    }

    /**
     * Initialize this exception with the given {@code message}.
     *
     * @param message The message describing the cause of the exception.
     */
    public NoHandlerForQueryException(String message) {
        super(message);
    }
}
