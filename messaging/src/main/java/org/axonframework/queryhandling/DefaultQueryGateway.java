/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.*;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import static java.util.Arrays.asList;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the QueryGateway interface that allows the registration of dispatchInterceptors.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.1
 */
public class DefaultQueryGateway implements QueryGateway {

    private final QueryBus queryBus;
    private final List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors;
    private final MessageNameResolver messageNameResolver;

    /**
     * Instantiate a {@link DefaultQueryGateway} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link QueryBus} is not {@code null}, and will throw an {@link AxonConfigurationException}
     * if it is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DefaultQueryGateway} instance
     */
    protected DefaultQueryGateway(Builder builder) {
        builder.validate();
        this.queryBus = builder.queryBus;
        this.dispatchInterceptors = builder.dispatchInterceptors;
        this.messageNameResolver = builder.messageNameResolver;
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} is defaulted to an empty list. The {@link QueryBus} is a
     * <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link DefaultQueryGateway}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <R, Q> CompletableFuture<R> query(@Nonnull String queryName,
                                             @Nonnull Q query,
                                             @Nonnull ResponseType<R> responseType) {
        QueryMessage<Q, R> queryMessage = asQueryMessage(query, queryName, responseType);

        CompletableFuture<QueryResponseMessage<R>> queryResponse = queryBus.query(processInterceptors(queryMessage));
        CompletableFuture<R> result = new CompletableFuture<>();
        result.whenComplete((r, e) -> {
            if (!queryResponse.isDone()) {
                queryResponse.cancel(true);
            }
        });
        queryResponse.exceptionally(cause -> asResponseMessage(responseType.responseMessagePayloadType(), cause))
                     .thenAccept(queryResponseMessage -> {
                         try {
                             if (queryResponseMessage.isExceptional()) {
                                 result.completeExceptionally(queryResponseMessage.exceptionResult());
                             } else {
                                 result.complete(queryResponseMessage.getPayload());
                             }
                         } catch (Exception e) {
                             result.completeExceptionally(e);
                         }
                     });
        return result;
    }

    /**
     * Creates a Query Response Message with given {@code declaredType} and {@code exception}.
     *
     * @param declaredType The declared type of the Query Response Message to be created
     * @param exception    The Exception describing the cause of an error
     * @param <R>          The type of the payload
     * @return a message containing exception result
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName name}.
     */
    @Deprecated
    private <R> QueryResponseMessage<R> asResponseMessage(Class<R> declaredType, Throwable exception) {
        return new GenericQueryResponseMessage<>(messageNameResolver.resolve(exception.getClass()),
                exception,
                declaredType);
    }

    @Override
    public <R, Q> Publisher<R> streamingQuery(String queryName, Q query, Class<R> responseType) {
        return Mono.fromSupplier(() -> asStreamingQueryMessage(query, queryName, responseType))
                   .flatMapMany(queryMessage -> queryBus.streamingQuery(processInterceptors(queryMessage)))
                   .map(Message::getPayload);
    }

    private <R, Q> StreamingQueryMessage<Q, R> asStreamingQueryMessage(Q query,
                                                                              String queryName,
                                                                              Class<R> responseType) {
        //noinspection unchecked
        return query instanceof Message<?>
                ? new GenericStreamingQueryMessage<>((Message<Q>) query,
                                                     queryName,
                                                     responseType)
                : new GenericStreamingQueryMessage<>(messageNameResolver.resolve(query),
                                                     queryName,
                                                     query,
                                                     responseType);
    }

    @Override
    public <R, Q> Stream<R> scatterGather(@Nonnull String queryName,
                                          @Nonnull Q query,
                                          @Nonnull ResponseType<R> responseType,
                                          long timeout,
                                          @Nonnull TimeUnit timeUnit) {
        QueryMessage<Q, R> queryMessage = asQueryMessage(query, queryName, responseType);
        return queryBus.scatterGather(processInterceptors(queryMessage), timeout, timeUnit)
                       .map(QueryResponseMessage::getPayload);
    }

    private <R, Q> QueryMessage<Q, R> asQueryMessage(Q query,
                                                            String queryName,
                                                            ResponseType<R> responseType) {
        //noinspection unchecked
        return query instanceof Message<?>
                ? new GenericQueryMessage<>((Message<Q>) query,
                                            queryName,
                                            responseType)
                : new GenericQueryMessage<>(messageNameResolver.resolve(query),
                                            queryName,
                                            query,
                                            responseType);
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(@Nonnull String queryName,
                                                                     @Nonnull Q query,
                                                                     @Nonnull ResponseType<I> initialResponseType,
                                                                     @Nonnull ResponseType<U> updateResponseType,
                                                                     int updateBufferSize) {
        SubscriptionQueryMessage<?, I, U> subscriptionQueryMessage =
                asSubscriptionQueryMessage(query, queryName, initialResponseType, updateResponseType);
        SubscriptionQueryMessage<?, I, U> interceptedQuery = processInterceptors(subscriptionQueryMessage);

        SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> result =
                queryBus.subscriptionQuery(interceptedQuery, updateBufferSize);

        return getSubscriptionQueryResult(result);
    }

    private <Q, I, U> SubscriptionQueryMessage<Q, I, U> asSubscriptionQueryMessage(
            Q query,
            String queryName,
            ResponseType<I> initialResponseType,
            ResponseType<U> updateResponseType
    ) {
        //noinspection unchecked
        return query instanceof Message<?>
                ? new GenericSubscriptionQueryMessage<>((Message<Q>) query,
                                                        queryName,
                                                        initialResponseType,
                                                        updateResponseType)
                : new GenericSubscriptionQueryMessage<>(messageNameResolver.resolve(query),
                                                        queryName,
                                                        query,
                                                        initialResponseType,
                                                        updateResponseType);
    }

    private <I, U> DefaultSubscriptionQueryResult<I, U> getSubscriptionQueryResult(
            SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> result) {
        return new DefaultSubscriptionQueryResult<>(
                result.initialResult()
                      .filter(initialResult -> Objects.nonNull(initialResult.getPayload()))
                      .map(Message::getPayload)
                      .onErrorMap(e -> e instanceof IllegalPayloadAccessException ? e.getCause() : e),
                result.updates()
                      .filter(update -> Objects.nonNull(update.getPayload()))
                      .map(SubscriptionQueryUpdateMessage::getPayload),
                result
        );
    }

    @Override
    public Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    @SuppressWarnings("unchecked")
    private <Q, R, T extends QueryMessage<Q, R>> T processInterceptors(T query) {
        T intercepted = query;
        for (MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor : dispatchInterceptors) {
            intercepted = (T) interceptor.handle(intercepted);
        }
        return intercepted;
    }

    /**
     * Builder class to instantiate a {@link DefaultQueryGateway}.
     * <p>
     * The {@code dispatchInterceptors} is defaulted to an empty list. The {@link QueryBus} is a
     * <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder {

        private QueryBus queryBus;
        private List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors =
                new CopyOnWriteArrayList<>();
        private MessageNameResolver messageNameResolver = new ClassBasedMessageNameResolver();

        /**
         * Sets the {@link QueryBus} to deliver {@link QueryMessage}s on received in this {@link QueryGateway}
         * implementation.
         *
         * @param queryBus a {@link QueryBus} to deliver {@link QueryMessage}s on received in this {@link QueryGateway}
         *                 implementation
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder queryBus(@Nonnull QueryBus queryBus) {
            assertNonNull(queryBus, "QueryBus may not be null");
            this.queryBus = queryBus;
            return this;
        }

        /**
         * Sets the {@link List} of {@link MessageDispatchInterceptor}s for {@link QueryMessage}s. Are invoked when a
         * query is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a query is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                MessageDispatchInterceptor<? super QueryMessage<?, ?>>... dispatchInterceptors) {
            return dispatchInterceptors(asList(dispatchInterceptors));
        }

        /**
         * Sets the {@link List} of {@link MessageDispatchInterceptor}s for {@link QueryMessage}s. Are invoked when a
         * query is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when a query is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors) {
            this.dispatchInterceptors = dispatchInterceptors != null && !dispatchInterceptors.isEmpty()
                    ? new CopyOnWriteArrayList<>(dispatchInterceptors)
                    : new CopyOnWriteArrayList<>();
            return this;
        }

        /**
         * Sets the {@link MessageNameResolver} to be used in order to resolve QualifiedName for published Event messages.
         * If not set, a {@link ClassBasedMessageNameResolver} is used by default.
         *
         * @param messageNameResolver which provides QualifiedName for Event messages
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageNameResolver(MessageNameResolver messageNameResolver) {
            assertNonNull(messageNameResolver, "MessageNameResolver may not be null");
            this.messageNameResolver = messageNameResolver;
            return this;
        }

        /**
         * Initializes a {@link DefaultQueryGateway} as specified through this Builder.
         *
         * @return a {@link DefaultQueryGateway} as specified through this Builder
         */
        public DefaultQueryGateway build() {
            return new DefaultQueryGateway(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(queryBus, "The QueryBus is a hard requirement and should be provided");
        }
    }
}
