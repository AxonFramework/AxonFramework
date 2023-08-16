/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ResultMessage;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Generic {@link SubscriptionQueryUpdateMessage} which holds incremental update of an subscription query.
 *
 * @param <U> type of incremental update
 * @author Milan Savic
 * @since 3.3
 */
public class GenericSubscriptionQueryUpdateMessage<U> extends GenericResultMessage<U>
        implements SubscriptionQueryUpdateMessage<U> {

    private static final long serialVersionUID = 5872479410321475147L;

    /**
     * Creates {@link GenericSubscriptionQueryUpdateMessage} from provided {@code payload} which represents incremental
     * update. The provided {@code payload} may not be {@code null}.
     *
     * @param payload incremental update
     * @param <T>     type of the {@link GenericSubscriptionQueryUpdateMessage}
     * @return created a {@link SubscriptionQueryUpdateMessage} with the given {@code payload}.
     */
    @SuppressWarnings("unchecked")
    public static <T> SubscriptionQueryUpdateMessage<T> asUpdateMessage(Object payload) {
        if (payload instanceof SubscriptionQueryUpdateMessage) {
            return (SubscriptionQueryUpdateMessage<T>) payload;
        } else if (payload instanceof ResultMessage) {
            ResultMessage<T> resultMessage = (ResultMessage<T>) payload;
            if (resultMessage.isExceptional()) {
                Throwable cause = resultMessage.exceptionResult();
                return new GenericSubscriptionQueryUpdateMessage<>(resultMessage.getPayloadType(),
                                                                   cause,
                                                                   resultMessage.getMetaData());
            }
            return new GenericSubscriptionQueryUpdateMessage<>(resultMessage);
        } else if (payload instanceof Message) {
            return new GenericSubscriptionQueryUpdateMessage<>((Message<T>) payload);
        }
        return new GenericSubscriptionQueryUpdateMessage<>((T) payload);
    }

    /**
     * Creates a {@link GenericSubscriptionQueryUpdateMessage} with the given {@code declaredType} and {@code exception}
     * result.
     *
     * @param declaredType The declared type of the Subscription Query Update Message to be created
     * @param exception    The exception describing the cause of an error
     * @param <T>          type of the {@link GenericSubscriptionQueryUpdateMessage}
     * @return a message containing exception result
     */
    public static <T> SubscriptionQueryUpdateMessage<T> asUpdateMessage(Class<T> declaredType, Throwable exception) {
        return new GenericSubscriptionQueryUpdateMessage<>(declaredType, exception, MetaData.emptyInstance());
    }

    /**
     * Initializes {@link GenericSubscriptionQueryUpdateMessage} with incremental update.
     *
     * @param payload payload of the message which represent incremental update
     */
    public GenericSubscriptionQueryUpdateMessage(U payload) {
        this(new GenericMessage<>(payload, MetaData.emptyInstance()));
    }

    /**
     * Initializes {@link GenericSubscriptionQueryUpdateMessage} with incremental update of provided {@code
     * declaredType}.
     *
     * @param declaredType the type of the update
     * @param payload      the payload of the update
     */
    public GenericSubscriptionQueryUpdateMessage(Class<U> declaredType, U payload) {
        this(declaredType, payload, MetaData.emptyInstance());
    }

    /**
     * Initializes {@link GenericSubscriptionQueryUpdateMessage} with incremental update of provided {@code
     * declaredType} and {@code metaData}.
     *
     * @param declaredType the type of the update
     * @param payload      the payload of the update
     * @param metaData     the metadata of the update
     */
    public GenericSubscriptionQueryUpdateMessage(Class<U> declaredType, U payload, Map<String, ?> metaData) {
        super(new GenericMessage<>(declaredType, payload, metaData));
    }

    /**
     * Initialize the subscription query update message with given {@code declaredType}, {@code exception} and {@code
     * metaData}.
     *
     * @param declaredType The declared type of the Subscription Query Update Message to be created
     * @param exception    The exception describing the cause of an error
     * @param metaData     The meta data to contain in the message
     */
    public GenericSubscriptionQueryUpdateMessage(Class<U> declaredType, Throwable exception, Map<String, ?> metaData) {
        super(new GenericMessage<>(declaredType, null, metaData), exception);
    }

    /**
     * Initializes a new decorator with given {@code delegate} message. The decorator delegates to the delegate for the
     * message's payload, metadata and identifier.
     *
     * @param delegate the message delegate
     */
    protected GenericSubscriptionQueryUpdateMessage(Message<U> delegate) {
        super(delegate);
    }

    @Override
    public GenericSubscriptionQueryUpdateMessage<U> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericSubscriptionQueryUpdateMessage<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public GenericSubscriptionQueryUpdateMessage<U> andMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericSubscriptionQueryUpdateMessage<>(getDelegate().andMetaData(metaData));
    }

    @Override
    protected String describeType() {
        return "GenericSubscriptionQueryUpdateMessage";
    }
}
