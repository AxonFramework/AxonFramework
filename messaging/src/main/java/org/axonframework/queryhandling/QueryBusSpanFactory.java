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

import org.axonframework.tracing.Span;

/**
 * Span factory that creates spans for the {@link QueryBus}. You can customize the spans of the bus by creating your own
 * implementation.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public interface QueryBusSpanFactory {

    /**
     * Creates a span for a query.
     *
     * @param queryMessage The query message being dispatched.
     * @param distributed  Whether the query is from a distributed source.
     * @return The span for the handling of the query.
     */
    Span createQuerySpan(QueryMessage<?, ?> queryMessage, boolean distributed);

    /**
     * Creates a span for a subscription query.
     *
     * @param queryMessage The subscription query message being dispatched.
     * @param distributed  Whether the subscription query is from a distributed source.
     * @return The span for the handling of the subscription query.
     */
    Span createSubscriptionQuerySpan(SubscriptionQueryMessage<?, ?, ?> queryMessage, boolean distributed);

    /**
     * Creates a span for processing a subscription query update that has been received from the server.
     *
     * @param updateMessage The update message being handled.
     * @param queryMessage  The subscription query message that the update is for.
     * @return The span for the processing of the subscription query update.
     */
    Span createSubscriptionQueryProcessUpdateSpan(SubscriptionQueryUpdateMessage<?> updateMessage,
                                                  SubscriptionQueryMessage<?, ?, ?> queryMessage);

    /**
     * Creates a span for a scatter-gather query.
     *
     * @param queryMessage The query message being handled.
     * @param distributed  Whether the query is from a distributed source.
     * @return The span for the handling of the scatter-gather query.
     */
    Span createScatterGatherSpan(QueryMessage<?, ?> queryMessage, boolean distributed);

    /**
     * Creates a span for one of the handlers of a scatter-gather query. There can be multiple for the same within one
     * application. The handler index is used to distinguish between them.
     *
     * @param queryMessage The query message being handled.
     * @param handlerIndex The index of the handler. Starts at 0.
     * @return The span for the handling of the scatter-gather query.
     */
    Span createScatterGatherHandlerSpan(QueryMessage<?, ?> queryMessage, int handlerIndex);

    /**
     * Creates a span for a streaming query.
     *
     * @param queryMessage The query message being dispatched.
     * @param distributed  Whether the query is from a distributed source.
     * @return The span for the handling of the streaming query.
     */
    Span createStreamingQuerySpan(QueryMessage<?, ?> queryMessage, boolean distributed);

    /**
     * Creates a span for processing a query. Distributed implementations can use this to create a span for the entire
     * query processing on the handling side.
     *
     * @param queryMessage The query message being handled.
     * @return The span for the processing of the query.
     */
    Span createQueryProcessingSpan(QueryMessage<?, ?> queryMessage);

    /**
     * Creates a span for processing a response. Distributed implementations can use this to create a span for the
     * entire response processing on the sending side.
     *
     * @param queryMessage The query message the response is for.
     * @return The span for the processing of the response.
     */
    Span createResponseProcessingSpan(QueryMessage<?, ?> queryMessage);

    /**
     * Propagates the context of the current span to the given {@code queryMessage}.
     *
     * @param queryMessage The query message to propagate the context to.
     * @param <T>          The type of the payload of the query message.
     * @param <R>          The type of the response of the query message.
     * @param <M>          The type of the query message.
     * @return The query message with the context of the current span.
     */
    <T, R, M extends QueryMessage<T, R>> M propagateContext(M queryMessage);
}
