/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.messaging.ResultMessage;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Message that contains the results of a Query. Results are represented as a Collection of result objects. When a query
 * resulted in a single result object, that object is contained as the sole element of the collection.
 *
 * @param <T> The type of object resulting from the query
 * @author Allard Buijze
 * @since 3.2
 */
public interface QueryResponseMessage<T> extends ResultMessage<T> {

    /**
     * Returns a copy of this QueryResponseMessage with the given {@code metaData}. The payload remains unchanged.
     *
     * @param metaData The new MetaData for the QueryResponseMessage
     * @return a copy of this message with the given MetaData
     */
    QueryResponseMessage<T> withMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this QueryResponseMessage with its MetaData merged with given {@code metaData}. The payload
     * remains unchanged.
     *
     * @param additionalMetaData The MetaData to merge into the QueryResponseMessage
     * @return a copy of this message with the given additional MetaData
     */
    QueryResponseMessage<T> andMetaData(@Nonnull Map<String, ?> additionalMetaData);
}
