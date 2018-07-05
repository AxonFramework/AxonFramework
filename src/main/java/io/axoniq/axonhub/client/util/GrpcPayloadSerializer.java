/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client.util;

import org.axonframework.messaging.Message;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.function.Function;

import static org.axonframework.serialization.MessageSerializer.serializePayload;

/**
 * Created by Sara Pellegrini on 28/06/2018.
 * sara.pellegrini@gmail.com
 */
public class GrpcPayloadSerializer implements Function<Message, io.axoniq.platform.SerializedObject> {

    private final GrpcObjectSerializer<Message> delegate;

    public GrpcPayloadSerializer(Serializer messageSerializer) {
        this(new GrpcObjectSerializer.Serializer<Message>() {
            @Override
            public <T> SerializedObject<T> serialize(Message object, Class<T> expectedRepresentation) {
                return serializePayload(object, messageSerializer, expectedRepresentation);
            }
        });
    }

    private GrpcPayloadSerializer(GrpcObjectSerializer.Serializer<Message> messageSerializer) {
        this(new GrpcObjectSerializer<>(messageSerializer));
    }

    private GrpcPayloadSerializer(GrpcObjectSerializer<Message> delegate) {
        this.delegate = delegate;
    }

    @Override
    public io.axoniq.platform.SerializedObject apply(Message message) {
        return delegate.apply(message);
    }
}
