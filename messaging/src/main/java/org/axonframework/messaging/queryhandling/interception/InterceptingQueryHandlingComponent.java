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

package org.axonframework.messaging.queryhandling.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryHandlingComponent;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;

import java.util.List;
import java.util.Set;

/**
 * A {@link QueryHandlingComponent} decorator that applies a list of
 * {@link MessageHandlerInterceptor MessageHandlerInterceptors} before delegating to the wrapped component.
 * <p>
 * This is used within modules to apply module-specific interceptors (such as the
 * {@link org.axonframework.messaging.core.interception.ApplicationContextHandlerInterceptor
 * ApplicationContextHandlerInterceptor}) at the component level, ensuring that handlers within a module resolve
 * components from the module's
 * {@link org.axonframework.common.configuration.Configuration Configuration} rather than the root one.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 * @see QueryMessageHandlerInterceptorChain
 */
@Internal
public class InterceptingQueryHandlingComponent implements QueryHandlingComponent {

    private final QueryHandlingComponent delegate;
    private final QueryMessageHandlerInterceptorChain chain;

    /**
     * Constructs an {@link InterceptingQueryHandlingComponent} wrapping the given {@code delegate} with the provided
     * {@code interceptors}.
     *
     * @param interceptors The list of handler interceptors to apply before the delegate handles the query.
     * @param delegate     The {@link QueryHandlingComponent} to delegate to after interceptors have been applied.
     */
    public InterceptingQueryHandlingComponent(
            @Nonnull List<MessageHandlerInterceptor<? super QueryMessage>> interceptors,
            @Nonnull QueryHandlingComponent delegate
    ) {
        this.delegate = delegate;
        this.chain = new QueryMessageHandlerInterceptorChain(interceptors, delegate);
    }

    @Nonnull
    @Override
    public Set<QualifiedName> supportedQueries() {
        return delegate.supportedQueries();
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> handle(@Nonnull QueryMessage query,
                                                       @Nonnull ProcessingContext context) {
        //noinspection unchecked | The interceptor chain wraps a QueryHandler returning MessageStream<QueryResponseMessage>
        return (MessageStream<QueryResponseMessage>) chain.proceed(query, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        delegate.describeTo(descriptor);
    }
}
