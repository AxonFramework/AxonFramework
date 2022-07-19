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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Generic implementation of the QueryMessage. Unless explicitly provided, it assumes the {@code queryName} of the
 * message is the fully qualified class name of the message's payload.
 *
 * @param <T> The type of payload expressing the query in this message
 * @param <R> The type of response expected from this query
 * @author Marc Gathier
 * @since 3.1
 */
@JsonIgnoreProperties("payloadType")
public class GenericQueryMessage<T, R> extends MessageDecorator<T> implements QueryMessage<T, R> {

    private static final long serialVersionUID = -3908412412867063631L;

    private final String queryName;
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    private final ResponseType<R> responseType;

    /**
     * Initializes the message with the given {@code payload} and expected {@code responseType}. The query name is set
     * to the fully qualified class name of the {@code payload}.
     *
     * @param payload      The payload expressing the query
     * @param responseType The expected response type of type {@link ResponseType}
     */
    public GenericQueryMessage(T payload, ResponseType<R> responseType) {
        this(payload, payload.getClass().getName(), responseType);
    }

    /**
     * Initializes the message with the given {@code payload}, {@code queryName} and expected {@code responseType}.
     *
     * @param payload      The payload expressing the query
     * @param queryName    The name identifying the query to execute
     * @param responseType The expected response type of type {@link ResponseType}
     */
    public GenericQueryMessage(T payload, String queryName, ResponseType<R> responseType) {
        this(new GenericMessage<>(payload, MetaData.emptyInstance()), queryName, responseType);
    }

    @JsonCreator
    public GenericQueryMessage(@JsonProperty("payload") T payload, @JsonProperty("queryName") String queryName, @JsonProperty("responseType") ResponseType<R> responseType, @JsonProperty("identifier") String identifier) {
        this(new GenericMessage<>(identifier, payload, MetaData.emptyInstance()), queryName, responseType);
    }

    /**
     * Initialize the Query Message, using given {@code delegate} as the carrier of payload and metadata and given
     * {@code queryName} and expecting the given {@code responseType}.
     *
     * @param delegate     The message containing the payload and meta data for this message
     * @param queryName    The name identifying the query to execute
     * @param responseType The expected response type of type {@link ResponseType}
     */
    public GenericQueryMessage(Message<T> delegate, String queryName, ResponseType<R> responseType) {
        super(delegate);
        this.responseType = responseType;
        this.queryName = queryName;
    }

    @Override
    public String getQueryName() {
        return queryName;
    }

    @Override
    public ResponseType<R> getResponseType() {
        return responseType;
    }

    @Override
    public QueryMessage<T, R> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericQueryMessage<>(getDelegate().withMetaData(metaData), queryName, responseType);
    }

    @Override
    public QueryMessage<T, R> andMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericQueryMessage<>(getDelegate().andMetaData(metaData), queryName, responseType);
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append(", queryName='")
                     .append(getQueryName())
                     .append('\'')
                     .append(", expectedResponseType='")
                     .append(getResponseType())
                     .append('\'');
    }

    @Override
    protected String describeType() {
        return "GenericQueryMessage";
    }
}
