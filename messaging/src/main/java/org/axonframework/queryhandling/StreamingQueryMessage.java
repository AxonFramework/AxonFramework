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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.reactivestreams.Publisher;

import java.util.Map;

/**
 * A {@link QueryMessage} used for initiating streaming queries.
 * <p>
 * It hard codes the {@link #getResponseType() response type} to an {@link PublisherResponseType} implementation.
 *
 * @param <P> The type of {@link #getPayload() payload} expressing the query in this {@link StreamingQueryMessage}.
 * @param <R> The type of {@link #getResponseType() response} expected from this {@link StreamingQueryMessage} streamed
 *            via {@link Publisher}.
 * @author Milan Savic
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface StreamingQueryMessage<P, R> extends QueryMessage<P, Publisher<R>> {

    @Override
    ResponseType<Publisher<R>> getResponseType();

    @Override
    StreamingQueryMessage<P, R> withMetaData(@Nonnull Map<String, ?> metaData);

    @Override
    StreamingQueryMessage<P, R> andMetaData(@Nonnull Map<String, ?> additionalMetaData);
}
