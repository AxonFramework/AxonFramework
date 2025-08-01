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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.util.Map;

/**
 * Generic implementation of the {@link QueryMessage} interface.
 *
 * @param <P> The type of {@link #payload() payload} expressing the query in this {@link QueryMessage}.
 * @param <R> The type of {@link #getResponseType() response} expected from this {@link QueryMessage}.
 * @author Marc Gathier
 * @author Steven van Beelen
 * @since 3.1.0
 */
public class GenericQueryMessage<P, R> extends MessageDecorator<P> implements QueryMessage<P, R> {

    private final ResponseType<R> responseType;


    /**
     * Constructs a {@link GenericQueryMessage} for the given {@code type}, {@code payload}, and
     * {@code responseType}.
     * <p>
     * The {@link MetaData} defaults to an empty instance. Initializes the message with the given {@code payload} and
     * expected {@code responseType}.
     *
     * @param type         The {@link MessageType type} for this {@link QueryMessage}.
     * @param payload      The payload of type {@code P} expressing the query for this {@link CommandMessage}.
     * @param responseType The expected {@link ResponseType response type} for this {@link QueryMessage}.
     */
    public GenericQueryMessage(@Nonnull MessageType type,
                               @Nonnull P payload,
                               @Nonnull ResponseType<R> responseType) {
        this(new GenericMessage<>(type, payload, MetaData.emptyInstance()), responseType);
    }

    /**
     * Constructs a {@code GenericQueryMessage} with given {@code delegate} and {@code responseType}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#payload() payload}, {@link Message#type() type},
     * {@link Message#getMetaData() metadata} and {@link Message#identifier() identifier} of the resulting
     * {@code GenericQueryMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate     The {@link Message} containing {@link Message#payload() payload},
     *                     {@link Message#type() type}, {@link Message#identifier() identifier} and
     *                     {@link Message#getMetaData() metadata} for the {@link QueryMessage} to reconstruct.
     * @param responseType The expected {@link ResponseType response type} for this {@link QueryMessage}.
     */
    public GenericQueryMessage(@Nonnull Message<P> delegate,
                               @Nonnull ResponseType<R> responseType) {
        super(delegate);
        this.responseType = responseType;
    }

    @Override
    public ResponseType<R> getResponseType() {
        return responseType;
    }

    @Override
    public QueryMessage<P, R> withMetaData(@Nonnull Map<String, String> metaData) {
        return new GenericQueryMessage<>(getDelegate().withMetaData(metaData), responseType);
    }

    @Override
    public QueryMessage<P, R> andMetaData(@Nonnull Map<String, String> metaData) {
        return new GenericQueryMessage<>(getDelegate().andMetaData(metaData), responseType);
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append(", expectedResponseType='")
                     .append(getResponseType())
                     .append('\'');
    }

    @Override
    protected String describeType() {
        return "GenericQueryMessage";
    }
}
