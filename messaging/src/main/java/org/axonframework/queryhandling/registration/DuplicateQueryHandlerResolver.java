/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.queryhandling.registration;

import org.axonframework.messaging.MessageHandler;
import org.axonframework.queryhandling.QuerySubscription;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Functional interface towards resolving the occurrence of a duplicate query handler being subscribed. As such it
 * ingests two {@link MessageHandler} instances and returns a list with the wanted handlers
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
@FunctionalInterface
public interface DuplicateQueryHandlerResolver {

    /**
     * Chooses what to do when a duplicate handler is registered, returning the handlers that should be selected for
     * query handling, or otherwise throwing an exception to reject registration altogether.
     *
     * @param queryName          The name of the query for which the duplicate was detected
     * @param registeredHandlers the {@link MessageHandler} instances already registered with the Query Bus
     * @param candidateHandler   the {@link MessageHandler} that is newly registered and conflicts with the existing
     *                           registrations
     * @return the resolved {@link MessageHandler} instances. It is up to the implementation to discard implementations
     * already in the list
     * @throws RuntimeException when registration should fail
     */
    List<QuerySubscription<?>> resolve(String queryName,
                                    Type responseType,
                                    List<QuerySubscription<?>> registeredHandlers,
                                    QuerySubscription<?> candidateHandler);
}
