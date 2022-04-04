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

package org.axonframework.eventhandling.replay;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MetaData;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Generic implementation of the {@link ResetContext}.
 *
 * @param <T> the type of payload contained in the message
 * @author Steven van Beelen
 * @since 4.4
 */
public class GenericResetContext<T> extends MessageDecorator<T> implements ResetContext<T> {

    private static final long serialVersionUID = -6872386525166762225L;

    /**
     * Returns the given {@code messageOrPayload} as a {@link ResetContext}. If {@code messageOrPayload} already
     * implements {@code ResetContext}, it is returned as-is. If it implements {@link Message}, {@code messageOrPayload}
     * will be cast to {@code Message} and current time is used to create a {@code ResetContext}. Otherwise, the given
     * {@code messageOrPayload} is wrapped into a {@link GenericResetContext} as its payload.
     *
     * @param messageOrPayload the payload to wrap or cast as {@link ResetContext}
     * @param <T>              the type of payload contained in the message
     * @return a {@link ResetContext} containing given {@code messageOrPayload} as payload, or the {@code
     * messageOrPayload} if it already implements {@code ResetContext}.
     */
    @SuppressWarnings("unchecked")
    public static <T> ResetContext<T> asResetContext(Object messageOrPayload) {
        if (messageOrPayload instanceof ResetContext) {
            return (ResetContext<T>) messageOrPayload;
        } else if (messageOrPayload instanceof Message) {
            return new GenericResetContext<>((Message<T>) messageOrPayload);
        }
        return new GenericResetContext<>((T) messageOrPayload);
    }

    /**
     * Instantiate a {@link GenericResetContext} containing the given {@code payload} and en empty {@link MetaData}
     * instance.
     *
     * @param payload the payload be included
     */
    public GenericResetContext(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    /**
     * Instantiate a {@link GenericResetContext} containing the given {@code payload} and {@link MetaData}.
     *
     * @param payload  the payload to be included
     * @param metaData the {@link MetaData} to be included
     */
    public GenericResetContext(T payload, Map<String, ?> metaData) {
        this(new GenericMessage<>(payload, metaData));
    }

    /**
     * @param message
     */
    public GenericResetContext(Message<T> message) {
        super(message);
    }

    @Override
    public GenericResetContext<T> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericResetContext<>(getDelegate().withMetaData(metaData));
    }

    @Override
    public GenericResetContext<T> andMetaData(@Nonnull Map<String, ?> additionalMetaData) {
        return new GenericResetContext<>(getDelegate().andMetaData(additionalMetaData));
    }

    @Override
    protected String describeType() {
        return "GenericResetContext";
    }
}
