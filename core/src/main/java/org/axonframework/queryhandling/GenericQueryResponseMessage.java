/*
 * Copyright (c) 2010-2017. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.queryhandling;

import org.axonframework.common.CollectionUtils;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MetaData;

import java.util.Map;

/**
 * QueryResponseMessage implementation that takes all properties as constructor parameters.
 *
 * @param <R> The type of return value contained in this response
 * @author Allard Buijze
 * @since 3.2
 */
public class GenericQueryResponseMessage<R> extends MessageDecorator<R> implements QueryResponseMessage<R> {

    private static final long serialVersionUID = -735698768536456937L;

    /**
     * Initialize the response message with given {@code result}.
     *
     * @param result The result reported by the Query Handler
     */
    @SuppressWarnings("unchecked")
    public GenericQueryResponseMessage(R result) {
        this((Class<R>) result.getClass(), result, MetaData.emptyInstance());
    }

    public GenericQueryResponseMessage(Class<R> declaredResultType, R result) {
        this(declaredResultType, result, MetaData.emptyInstance());
    }

    /**
     * Initialize the response message with given {@code result}.
     *
     * @param result   The result reported by the Query Handler
     * @param metaData The meta data to contain in the message
     */
    public GenericQueryResponseMessage(R result, Map<String, ?> metaData) {
        super(new GenericMessage<>(result, metaData));
    }

    public GenericQueryResponseMessage(Class<R> declaredResultType, R result, Map<String, ?> metaData) {
        super(new GenericMessage<>(declaredResultType, result, metaData));
    }

    /**
     * Copy-constructor that takes the payload, meta data and message identifier of the given {@code delegate} for this
     * message.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate The message to retrieve message details from
     */
    public GenericQueryResponseMessage(Message<R> delegate) {
        super(delegate);
    }

    /**
     * Creates a QueryResponseMessage for the given {@code result}. If result already implements QueryResponseMessage,
     * it is returned directly. Otherwise a new QueryResponseMessage is created with the result as payload. An attempt
     * is made to detect collection-like structures (see {@link CollectionUtils#asCollection(Object)}) in the given
     * {@code result}, converting it to a Collection is possible.
     *
     * @param result The result of a Query, to be wrapped in a QueryResponseMessage
     * @param <R>    The type of response expected
     * @return a QueryResponseMessage for the given {@code result}, or the result itself, if already a
     * QueryResponseMessage.
     */
    @SuppressWarnings("unchecked")
    public static <R> QueryResponseMessage<R> asResponseMessage(Object result) {
        if (result instanceof QueryResponseMessage) {
            return (QueryResponseMessage<R>) result;
        } else {
            return new GenericQueryResponseMessage(result);
        }
    }

    /**
     *
     * @param declaredType
     * @param result
     * @param <R>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <R> QueryResponseMessage<R> asNullableResponseMessage(Class<R> declaredType, Object result) {
        if (result instanceof QueryResponseMessage) {
            return (QueryResponseMessage<R>) result;
        } else {
            return new GenericQueryResponseMessage(declaredType, result);
        }
    }

    @Override
    public QueryResponseMessage<R> withMetaData(Map<String, ?> metaData) {
        return new GenericQueryResponseMessage<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public QueryResponseMessage<R> andMetaData(Map<String, ?> additionalMetaData) {
        return new GenericQueryResponseMessage<>(getDelegate().andMetaData(additionalMetaData));
    }
}
