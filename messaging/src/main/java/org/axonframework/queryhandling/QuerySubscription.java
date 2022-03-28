/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.lang.reflect.Type;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Encapsulates the identifying fields of a Query Handler when one is subscribed to the {@link
 * org.axonframework.queryhandling.QueryBus}. As such contains the response type of the query handler and the complete
 * handler itself. The first is typically used by the QueryBus to select the right query handler when a query comes in.
 * The latter is used to perform the actual query.
 *
 * @param <R> the type of response this query subscription contains
 * @author Steven van Beelen
 * @since 3.2
 */
public class QuerySubscription<R> {

    private final Type responseType;
    private final MessageHandler<? super QueryMessage<?, R>> queryHandler;

    /**
     * Instantiate a {@link QuerySubscription} with a specific {@code responseType} and {@code queryHandler}.
     *
     * @param responseType a {@link java.lang.reflect.Type} as the response type of this subscription
     * @param queryHandler the subscribed {@link org.axonframework.messaging.MessageHandler}
     */
    public QuerySubscription(@Nonnull Type responseType, @Nonnull MessageHandler<? super QueryMessage<?, R>> queryHandler) {
        this.responseType = responseType;
        this.queryHandler = queryHandler;
    }

    /**
     * Retrieve the response type of this subscription as a {@link java.lang.reflect.Type}.
     *
     * @return the response type as a {@link java.lang.reflect.Type} tied to this subscription
     */
    public Type getResponseType() {
        return responseType;
    }

    /**
     * Check if this {@link QuerySubscription} can handle the given {@code queryResponseType}, by calling the {@link
     * ResponseType#matches(Type)} function on it and providing the set {@code responseType} of this subscription.
     *
     * @param queryResponseType a {@link ResponseType} to match this subscriptions it's {@code responseType} against
     * @return true of the given {@code queryResponseType} its {@link ResponseType#matches(Type)} returns true, false if
     * otherwise
     */
    public boolean canHandle(@Nonnull ResponseType<?> queryResponseType) {
        return queryResponseType.matches(responseType);
    }

    /**
     * Retrieve the query handler of this subscription as a {@link org.axonframework.messaging.MessageHandler}.
     *
     * @return the {@link org.axonframework.messaging.MessageHandler} tied to this subscription
     */
    public MessageHandler<? super QueryMessage<?, R>> getQueryHandler() {
        return queryHandler;
    }

    @Override
    public int hashCode() {
        return Objects.hash(responseType, queryHandler);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final QuerySubscription<?> other = (QuerySubscription<?>) obj;
        return Objects.equals(this.responseType, other.responseType)
                && Objects.equals(this.queryHandler, other.queryHandler);
    }
}
