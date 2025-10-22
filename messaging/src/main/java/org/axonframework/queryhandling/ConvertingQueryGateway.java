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
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A {@link QueryGateway} implementation that pulls any query responses through a {@link MessageConverter}.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */ // TODO #613 implement
    // FIXME remove
public class ConvertingQueryGateway implements QueryGateway {

    private final QueryGateway delegate;
    private final MessageConverter converter;

    public ConvertingQueryGateway(@Nonnull QueryGateway delegate,
                                  @Nonnull MessageConverter converter) {
        this.delegate = requireNonNull(delegate, "The delegate must not be null.");
        this.converter = requireNonNull(converter, "The MessageConverter must not be null.");
    }

    @Nonnull
    @Override
    public <R> CompletableFuture<R> query(@Nonnull Object query, @Nonnull Class<R> responseType,
                                          @Nullable ProcessingContext context) {
        return delegate.query(query, responseType, context);
    }

    @Nonnull
    @Override
    public <R> CompletableFuture<List<R>> queryMany(@Nonnull Object query, @Nonnull Class<R> responseType,
                                                    @Nullable ProcessingContext context) {
        return null;
    }


    @Nonnull
    @Override
    public <T> Publisher<T> subscriptionQuery(@Nonnull Object query, @Nonnull Class<T> responseType,
                                              @Nonnull Function<QueryResponseMessage, T> mapper,
                                              @Nullable ProcessingContext context, int updateBufferSize) {
        return null;
    }


    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {

    }
}
