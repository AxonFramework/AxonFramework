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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryUpdate;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.event.util.GrpcExceptionParser;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResponse;
import org.axonframework.queryhandling.SubscriptionQueryResponseMessages;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.tracing.QueryBusSpanFactory;
import org.axonframework.tracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

/**
 * A {@link SubscriptionQueryResponse} that emits initial response and update when subscription query response message
 * is received.
 *
 * @author Sara Pellegrini
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @author Allard Buijze
 * @since 4.0
 */
// TODO #3488 - Pick up as part of AxonServerQueryBus resolution
public class AxonServerSubscriptionQueryResponseMessages
        implements SubscriptionQueryResponseMessages {

    private final Logger logger = LoggerFactory.getLogger(AxonServerSubscriptionQueryResponseMessages.class);

    private final Mono<QueryResponseMessage> initialResult;
    private final io.axoniq.axonserver.connector.query.SubscriptionQueryResult result;
    private final Flux<SubscriptionQueryUpdateMessage> updates;

    /**
     * Instantiate a {@link AxonServerSubscriptionQueryResponseMessages} which will emit its initial response and the
     * updates of the subscription query.
     */
    public AxonServerSubscriptionQueryResponseMessages(final SubscriptionQueryMessage queryMessage,
                                                       final io.axoniq.axonserver.connector.query.SubscriptionQueryResult result,
                                                       final SubscriptionMessageSerializer subscriptionSerializer,
                                                       final QueryBusSpanFactory spanFactory,
                                                       final Span parentSpan) {
        updates = Flux.<SubscriptionQueryUpdateMessage>create(fluxSink -> {
            fluxSink.onRequest(count -> {
                for (int i = 0; i < count; i++) {
                    QueryUpdate next = result.updates().nextIfAvailable();
                    if (next != null) {
                        publishQueryUpdate(queryMessage, subscriptionSerializer, spanFactory, fluxSink, next);
                    } else {
                        if (result.updates().isClosed()) {
                            completeFlux(fluxSink, result.updates().getError().orElse(null));
                        }
                        break;
                    }
                }
            });

            fluxSink.onDispose(() -> {
                logger.debug("Flux was disposed. Will close subscription query");
                result.updates().close();
            });

            result.updates().onAvailable(() -> {
                if (fluxSink.requestedFromDownstream() > 0) {
                    QueryUpdate next = result.updates().nextIfAvailable();
                    if (next != null) {
                        publishQueryUpdate(queryMessage, subscriptionSerializer, spanFactory, fluxSink, next);
                    }
                } else {
                    logger.trace("Not sending update to Flux Sink. Not enough info requested");
                }
                if (result.updates().isClosed()) {
                    completeFlux(fluxSink, result.updates().getError().orElse(null));
                }
            });
        }).doOnError(e -> result.updates().close());

        this.initialResult = Mono.fromCompletionStage(result::initialResult)
                                 .onErrorMap(GrpcExceptionParser::parse)
                                 .doOnError(parentSpan::recordException)
                                 .doOnTerminate(parentSpan::end)
                                 .map(subscriptionSerializer::deserialize);
        this.result = result;
    }

    private void publishQueryUpdate(final SubscriptionQueryMessage queryMessage,
                                    SubscriptionMessageSerializer subscriptionSerializer,
                                    QueryBusSpanFactory spanFactory,
                                    FluxSink<SubscriptionQueryUpdateMessage> fluxSink, QueryUpdate next) {
        SubscriptionQueryUpdateMessage message = subscriptionSerializer.deserialize(next);
        spanFactory.createSubscriptionQueryProcessUpdateSpan(message, queryMessage)
                   .run(() -> fluxSink.next(message));
    }

    private void completeFlux(FluxSink<SubscriptionQueryUpdateMessage> fluxSink, Throwable error) {
        if (error != null) {
            fluxSink.error(error);
        } else {
            fluxSink.complete();
        }
    }

    @Nonnull
    @Override
    public Flux<QueryResponseMessage> initialResult() {
        return Flux.from(initialResult);
    }

    @Nonnull
    @Override
    public Flux<SubscriptionQueryUpdateMessage> updates() {
        return updates;
    }

    @Override
    public void close() {
        result.updates().close();
    }
}
