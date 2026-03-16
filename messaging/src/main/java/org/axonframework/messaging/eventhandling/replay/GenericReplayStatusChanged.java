/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.replay;

import org.axonframework.common.ObjectUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDecorator;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

/**
 * Generic implementation of the {@link ReplayStatusChanged} interface.
 *
 * @author Simon Zambrovski
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 5.1.0
 */
@Internal
public class GenericReplayStatusChanged extends MessageDecorator implements ReplayStatusChanged {

    private final ReplayStatus status;

    /**
     * Constructs a {@code GenericReplayStatusChange} for the given {@code status} and {@code payload}.
     * <p>
     * The {@link Metadata} defaults to an empty instance.
     *
     * @param status  the status changed to as notified by this message
     * @param payload the payload for this {@link ReplayStatusChanged}
     */
    public GenericReplayStatusChanged(ReplayStatus status,
                                      @Nullable Object payload) {
        this(status, payload, Metadata.emptyInstance());
    }

    /**
     * Constructs a {@code GenericReplayStatusChange} for the given {@code status}, {@code payload}, and
     * {@code metadata}.
     *
     * @param status   the status changed to as notified by this message
     * @param payload  the payload for this {@link ReplayStatusChanged}
     * @param metadata the metadata for this {@link ReplayStatusChanged}
     */
    public GenericReplayStatusChanged(ReplayStatus status,
                                      @Nullable Object payload,
                                      Map<String, @Nullable String> metadata) {
        this(status, new GenericMessage(new MessageType(ReplayStatusChanged.class), payload, metadata));
    }

    /**
     * Constructs a {@code GenericReplayStatusChange} for the given {@code delegate}, intended to reconstruct another
     * {@link ReplayStatusChanged}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param status   the status changed to as notified by this message
     * @param delegate the {@link Message} containing {@link Message#payload() payload}, {@link Message#type() type},
     *                 {@link Message#identifier() identifier} and {@link Message#metadata() metadata} for the
     *                 {@link EventMessage} to reconstruct
     */
    public GenericReplayStatusChanged(ReplayStatus status, Message delegate) {
        super(delegate);
        this.status = Objects.requireNonNull(status);
    }

    @Override
    public ReplayStatus status() {
        return this.status;
    }

    @Override
    public ReplayStatusChanged withMetadata(Map<String, String> metadata) {
        return new GenericReplayStatusChanged(this.status, delegate().withMetadata(metadata));
    }

    @Override
    public ReplayStatusChanged andMetadata(Map<String, @Nullable String> additionalMetadata) {
        return new GenericReplayStatusChanged(this.status, delegate().andMetadata(additionalMetadata));
    }

    @Override
    public ReplayStatusChanged withConvertedPayload(Type type, Converter converter) {
        Object convertedPayload = this.payloadAs(type, converter);
        if (ObjectUtils.nullSafeTypeOf(convertedPayload).isAssignableFrom(payloadType())) {
            return this;
        }
        Message delegate = delegate();
        return new GenericReplayStatusChanged(
                this.status,
                new GenericMessage(delegate.identifier(), delegate.type(), convertedPayload, delegate.metadata())
        );
    }

    @Override
    protected String describeType() {
        return "GenericReplayStatusChange";
    }
}
