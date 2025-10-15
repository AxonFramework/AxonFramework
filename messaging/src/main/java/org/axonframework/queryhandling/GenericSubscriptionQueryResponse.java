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

package org.axonframework.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.function.Function;

/**
 * Generic implementation of the {@link SubscriptionQueryResponse} interface.
 *
 * @param <I> The type of payload contained in the {@link #initialResult()} result.
 * @param <U> The type of payload contained in the {@link #updates()}.
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3.0
 */
public class GenericSubscriptionQueryResponse<I, U> implements SubscriptionQueryResponse<I, U> {

    private final SubscriptionQueryResponseMessages response;
    private final Function<Message, I> initialConverter;
    private final Function<Message, U> updateConverter;

    /**
     * Constructs a {@code GenericSubscriptionQueryResponse} using the given {@code response} to return the
     * {@link #initialResult() initial result} and {@link #updates()}.
     * <p>
     * Uses the given {@code initialConverter} to map the {@link QueryResponseMessage QueryResponseMessages} from
     * {@link SubscriptionQueryResponseMessages#initialResult()} and uses the given {@code updateConverter} to map the
     * {@link SubscriptionQueryUpdateMessage SubscriptionQueryUpdateMessages} from the
     * {@link SubscriptionQueryResponseMessages#updates()}.
     *
     * @param response         The response to retrieve the {@link #initialResult() initial result} and
     *                         {@link #updates()} from, mapped with the given {@code initialConverter} and
     *                         {@code updateConverter} respectively.
     * @param initialConverter The converter used to map the {@link SubscriptionQueryResponseMessages#initialResult()}
     *                         with.
     * @param updateConverter  The converter used to map the {@link SubscriptionQueryResponseMessages#updates()} with.
     */
    public GenericSubscriptionQueryResponse(@Nonnull SubscriptionQueryResponseMessages response,
                                            @Nonnull Function<Message, I> initialConverter,
                                            @Nonnull Function<Message, U> updateConverter) {
        this.response = Objects.requireNonNull(response, "The response stream must not be null.");
        this.initialConverter = Objects.requireNonNull(initialConverter, "The initial converter must not be null.");
        this.updateConverter = Objects.requireNonNull(updateConverter, "The update converter must not be null.");
    }

    @Nonnull
    @Override
    public Flux<I> initialResult() {
        return response.initialResult().mapNotNull(
                response -> Objects.isNull(response.payload()) ? null : initialConverter.apply(response)
        );
    }

    @Nonnull
    @Override
    public Flux<U> updates() {
        return response.updates().mapNotNull(
                response -> Objects.isNull(response.payload()) ? null : updateConverter.apply(response)
        );
    }

    @Override
    public void close() {
        response.close();
    }
}
