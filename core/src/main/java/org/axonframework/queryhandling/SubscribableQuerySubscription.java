/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.messaging.MessageHandler;

import java.lang.reflect.Type;

/**
 * Defines the necessary info for {@link QueryBus} in order to keep subscriptions for subscribable query handlers. Those
 * information are {@code responseType}, {@code updateType} and handlers to be invoked.
 *
 * @param <I> the type of initial response of query handler
 * @param <U> the type of incremental responses of query handler
 * @author Milan Savic
 * @since 3.3
 */
class SubscribableQuerySubscription<I, U> extends QuerySubscription<I> {

    private final Type updateType;
    private final SubscriptionQueryMessageHandler<? super QueryMessage<?, I>, I, U> subscriptionQueryMessageHandler;

    /**
     * Instantiate a {@link SubscribableQuerySubscription} with a specific {@code responseType}, {@code updateType}
     * and {@code queryHandler}.
     *
     * @param responseType a {@link Type} as the response type of this subscription
     * @param updateType   a {@link Type} as the update type of this subscription
     * @param queryHandler the subscribed {@link MessageHandler}
     */
    SubscribableQuerySubscription(Type responseType,
                                  Type updateType,
                                  SubscriptionQueryMessageHandler<? super QueryMessage<?, I>, I, U> queryHandler) {
        super(responseType, m -> {
            QueryUpdateEmitter<U> emitter = new QueryUpdateEmitter<U>() {
                @Override
                public void emit(U update) {
                    // this is empty implementation, since regular query handler will not invoke it
                }

                @Override
                public void complete() {
                    // this is empty implementation, since regular query handler will not invoke it
                }

                @Override
                public void error(Throwable error) {
                    // this is empty implementation, since regular query handler will not invoke it
                }
            };
            return queryHandler.handle(m, emitter);
        });
        this.updateType = updateType;
        this.subscriptionQueryMessageHandler = queryHandler;
    }

    /**
     * Gets the subscription query handler.
     *
     * @return the subscription query handler
     */
    public SubscriptionQueryMessageHandler<? super QueryMessage<?, I>, I, U> getSubscriptionQueryHandler() {
        return subscriptionQueryMessageHandler;
    }

    /**
     * Gets the update type.
     *
     * @return the update type
     */
    public Type getUpdateType() {
        return updateType;
    }
}
