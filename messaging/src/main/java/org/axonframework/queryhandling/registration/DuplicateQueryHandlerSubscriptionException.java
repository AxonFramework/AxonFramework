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

package org.axonframework.queryhandling.registration;

import org.axonframework.common.AxonNonTransientException;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.queryhandling.QuerySubscription;

import java.lang.reflect.Type;

/**
 * Exception indicating a duplicate Query Handler was subscribed whilst this behavior is purposefully guarded against.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class DuplicateQueryHandlerSubscriptionException extends AxonNonTransientException {
    private static final long serialVersionUID = -3275563671982758910L;

    /**
     * Initialize a duplicate query handler subscription exception using the given {@code initialHandler} and {@code
     * duplicateHandler} to form a specific message.
     *
     * @param queryName        The name of the query for which the duplicate was detected
     * @param responseType     The response type of the query for which the duplicate was detected
     * @param initialHandler   the initial {@link MessageHandler} for which a duplicate was encountered
     * @param duplicateHandler the duplicated {@link MessageHandler}
     */
    public DuplicateQueryHandlerSubscriptionException(String queryName,
                                                      Type responseType,
                                                      QuerySubscription<?> initialHandler,
                                                      QuerySubscription<?> duplicateHandler) {
        this(String.format("A duplicate Query Handler for query [%s] and response type [%s] has been subscribed residing in class [%s]"
                                   + " that would override an identical handler in class [%s].",
                           queryName,
                           responseType.getTypeName(),
                           duplicateHandler.getQueryHandler().getTargetType().getName(),
                           initialHandler.getQueryHandler().getTargetType().getName()
        ));
    }

    /**
     * Initializes a duplicate query handler subscription exception using the given {@code message}.
     *
     * @param message the message describing the exception
     */
    public DuplicateQueryHandlerSubscriptionException(String message) {
        super(message);
    }

}
