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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;

import java.util.Optional;
import java.util.function.Function;

/**
 * Implementation of the {@link MessageStream} that maps the {@link Entry entries}.
 *
 * @param <DM> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @param <RM> The type of {@link Message} contained in the {@link Entry} as a result of mapping.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class MappedMessageStream<DM extends Message<?>, RM extends Message<?>> extends DelegatingMessageStream<DM, RM> {

    private final Function<Entry<DM>, Entry<RM>> mapper;
    private final MessageStream<DM> delegate;

    /**
     * Construct a {@link MessageStream stream} mapping the {@link Entry entries} of the given
     * {@code delegate MessageStream} to entries containing {@link Message Messages} of type {@code RM}.
     *
     * @param delegate The {@link MessageStream stream} who's {@link Entry entries} are mapped with the given
     *                 {@code mapper}.
     * @param mapper   The {@link Function} mapping {@link Entry entries}.
     */
    MappedMessageStream(@Nonnull MessageStream<DM> delegate,
                        @Nonnull Function<Entry<DM>, Entry<RM>> mapper) {
        super(delegate);
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public Optional<Entry<RM>> next() {
        return delegate.next().map(mapper);
    }

    /**
     * Extension of the {@code MappedMessageStream} that maps the entry in a single-value stream. This allows the
     * wrapped stream to also implement {@link MessageStream.Single}.
     *
     * @param <DM> The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @param <RM> The type of {@link Message} contained in the {@link Entry} as a result of mapping.
     */
    static class Single<DM extends Message<?>, RM extends Message<?>> extends MappedMessageStream<DM, RM> implements
            MessageStream.Single<RM> {

        /**
         * Construct a {@link MessageStream stream} mapping only the first {@link Entry} of the given
         * {@code delegate MessageStream} to an entry containing a {@link Message} of type {@code RM}.
         *
         * @param delegate The {@link MessageStream stream} from which only the first {@link Entry} is mapped with the
         *                 given {@code mapper}.
         * @param mapper   The {@link Function} mapping the first {@link Entry} from the given {@code delegate}.
         */
        Single(@Nonnull MessageStream.Single<DM> delegate, @Nonnull Function<Entry<DM>, Entry<RM>> mapper) {
            super(delegate, mapper);
        }
    }
}
