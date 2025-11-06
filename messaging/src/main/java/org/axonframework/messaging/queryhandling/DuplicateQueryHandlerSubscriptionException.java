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

package org.axonframework.messaging.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.QualifiedName;

/**
 * Exception indicating a duplicate {@link QueryHandler} was subscribed.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DuplicateQueryHandlerSubscriptionException extends RuntimeException {

    /**
     * Initialize a duplicate {@link QueryHandler} subscription exception using the given {@code initialHandler} and
     * {@code duplicateHandler} to form a specific message.
     *
     * @param name             The name of the query handler for which the duplicate was detected.
     * @param initialHandler   the initial {@link QueryHandler} for which a duplicate was encountered.
     * @param duplicateHandler The duplicated {@link QueryHandler}.
     */
    public DuplicateQueryHandlerSubscriptionException(@Nonnull QualifiedName name,
                                                      @Nonnull QueryHandler initialHandler,
                                                      @Nonnull QueryHandler duplicateHandler) {
        this(String.format("Duplicate subscription for query handler [%s] detected. "
                                   + "Registration of handler [%s]  conflicts with previously registered handler [%s].",
                           name, initialHandler, duplicateHandler));
    }

    /**
     * Initializes a {@code DuplicateQueryHandlerSubscriptionException} using the given {@code message}.
     *
     * @param message The message describing the exception.
     */
    public DuplicateQueryHandlerSubscriptionException(String message) {
        super(message);
    }
}
