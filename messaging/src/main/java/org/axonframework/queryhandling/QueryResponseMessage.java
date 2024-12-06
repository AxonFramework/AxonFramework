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
import org.axonframework.messaging.ResultMessage;

import java.util.Map;

/**
 * A {@link ResultMessage} implementation that contains the result of query handling.
 *
 * @param <R> The type of {@link #getPayload() payload} contained in this {@link QueryResponseMessage}.
 * @author Allard Buijze
 * @since 3.2.0
 */
public interface QueryResponseMessage<R> extends ResultMessage<R> {

    QueryResponseMessage<R> withMetaData(@Nonnull Map<String, ?> metaData);

    QueryResponseMessage<R> andMetaData(@Nonnull Map<String, ?> additionalMetaData);
}
