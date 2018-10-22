/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MetaData;

import java.util.Map;

/**
 * Generic {@link SubscriptionQueryUpdateMessage} which holds incremental update of an subscription query.
 *
 * @param <U> type of incremental update
 * @author Milan Savic
 * @since 3.3
 */
public class GenericSubscriptionQueryUpdateMessage<U> extends MessageDecorator<U>
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
        if (SubscriptionQueryUpdateMessage.class.isInstance(payload)) {
            return (SubscriptionQueryUpdateMessage<T>) payload;
        } else if (payload instanceof Message) {
            return new GenericSubscriptionQueryUpdateMessage<>((Message) payload);
        }
        return new GenericSubscriptionQueryUpdateMessage<>((T) payload);
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
     * Initializes a new decorator with given {@code delegate} message. The decorator delegates to the delegate for
     * the message's payload, metadata and identifier.
     *
     * @param delegate the message delegate
     */
    protected GenericSubscriptionQueryUpdateMessage(Message<U> delegate) {
        super(delegate);
    }

    @Override
    public SubscriptionQueryUpdateMessage<U> withMetaData(Map<String, ?> metaData) {
        return new GenericSubscriptionQueryUpdateMessage<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public SubscriptionQueryUpdateMessage<U> andMetaData(Map<String, ?> metaData) {
        return new GenericSubscriptionQueryUpdateMessage<>(getDelegate().andMetaData(metaData));
    }

    @Override
    protected String describeType() {
        return "GenericSubscriptionQueryUpdateMessage";
    }
}
