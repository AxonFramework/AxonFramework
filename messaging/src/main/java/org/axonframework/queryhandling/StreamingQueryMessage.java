/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.queryhandling;

import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.reactivestreams.Publisher;

import java.util.Map;

/**
 * A special type of {@link QueryMessage} used for initiating streaming queries. It's special since it hard codes the
 * response type to {@link PublisherResponseType}.
 *
 * @param <Q> the type of streaming query payload
 * @param <R> the type of the result streamed via {@link Publisher}
 * @author Milan Savic
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public interface StreamingQueryMessage<Q, R> extends QueryMessage<Q, Publisher<R>> {

    @Override
    ResponseType<Publisher<R>> getResponseType();

    @Override
    StreamingQueryMessage<Q, R> withMetaData(Map<String, ?> metaData);

    @Override
    StreamingQueryMessage<Q, R> andMetaData(Map<String, ?> additionalMetaData);
}
