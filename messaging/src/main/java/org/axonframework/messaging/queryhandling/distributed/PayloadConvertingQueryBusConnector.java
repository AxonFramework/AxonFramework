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

package org.axonframework.messaging.queryhandling.distributed;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * Connector implementation that converts the payload of outgoing messages into the expected format. This is generally a
 * {@code byte[]} or another serialized form.
 *
 * @author Jan Galinski
 * @since 5.0.0
 */
public class PayloadConvertingQueryBusConnector extends DelegatingQueryBusConnector {

    private final MessageConverter converter;
    private final Class<?> targetType;

    /**
     * Initialize the {@code PayloadConvertingConnector} to use given {@code converter} to convert each Message's
     * payload into {@code targetType} before passing it to given {@code delegate}.
     *
     * @param delegate   The delegate to pass converted messages to.
     * @param converter  The converter to use to convert each Message's payload.
     * @param targetType The desired representation of forwarded Message's payload.
     */
    public PayloadConvertingQueryBusConnector(@Nonnull QueryBusConnector delegate,
                                              @Nonnull MessageConverter converter,
                                              @Nonnull Class<?> targetType) {
        super(delegate);

        this.converter = requireNonNull(converter, "The converter must not be null.");
        this.targetType = requireNonNull(targetType, "The targetType must not be null.");
    }


    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query, @Nullable ProcessingContext context) {
        return delegate.query(query.withConvertedPayload(targetType, converter), context);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull QueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        return delegate.subscriptionQuery(query.withConvertedPayload(targetType, converter), context, updateBufferSize);
    }

    @Override
    public void onIncomingQuery(@Nonnull Handler handler) {
        delegate.onIncomingQuery(new Handler() {

            @Override
            public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query) {
                return handler.query(query)
                              .mapMessage(rm -> rm.withConvertedPayload(targetType, converter));
            }

            @Nonnull
            @Override
            public Registration registerUpdateHandler(@Nonnull QueryMessage subscriptionQueryMessage,
                                                      @Nonnull UpdateCallback updateCallback) {
                return handler.registerUpdateHandler(subscriptionQueryMessage, new UpdateCallback() {
                    @Nonnull
                    @Override
                    public CompletableFuture<Void> sendUpdate(@Nonnull SubscriptionQueryUpdateMessage update) {
                        return updateCallback.sendUpdate(update.withConvertedPayload(targetType, converter));
                    }

                    @Override
                    public CompletableFuture<Void> complete() {
                        return updateCallback.complete();
                    }

                    @Override
                    public CompletableFuture<Void> completeExceptionally(@Nonnull Throwable cause) {
                        return updateCallback.completeExceptionally(cause);
                    }
                });
            }
        });
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("converter", converter);
        descriptor.describeProperty("targetType", targetType);
    }
}
