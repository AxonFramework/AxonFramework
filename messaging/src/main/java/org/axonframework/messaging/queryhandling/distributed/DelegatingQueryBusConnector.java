/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;

import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * A {@link QueryBusConnector} implementation that wraps another {@link QueryBusConnector} and delegates all calls to
 * it.
 * <p>
 * This can be used to add additional functionality through decoration to a {@link QueryBusConnector} without having to
 * implement all methods again.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public abstract class DelegatingQueryBusConnector implements QueryBusConnector {

    protected final QueryBusConnector delegate;

    /**
     * Initialize the delegating {@link QueryBusConnector} to delegate all calls to the given {@code delegate}.
     *
     * @param delegate The {@link QueryBusConnector} to delegate all calls to.
     */
    protected DelegatingQueryBusConnector(@Nonnull QueryBusConnector delegate) {
        this.delegate = requireNonNull(delegate, "The delegate must not be null.");
    }

    // region [QueryBus]

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query, @Nullable ProcessingContext context) {
        return delegate.query(query, context);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> subscriptionQuery(@Nonnull QueryMessage query,
                                                                 @Nullable ProcessingContext context,
                                                                 int updateBufferSize) {
        return delegate.subscriptionQuery(query, context, updateBufferSize);
    }

    // endregion


    // region [Connector]

    @Override
    public CompletableFuture<Void> subscribe(@Nonnull QualifiedName name) {
        return delegate.subscribe(name);
    }

    @Override
    public boolean unsubscribe(@Nonnull QualifiedName name) {
        return delegate.unsubscribe(name);
    }

    @Override
    public void onIncomingQuery(@Nonnull Handler handler) {
        delegate.onIncomingQuery(handler);
    }
    // endregion

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}
