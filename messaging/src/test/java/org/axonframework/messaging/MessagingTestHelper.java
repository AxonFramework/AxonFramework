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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;

/**
 * Helper to create messages in tests.
 */
public class MessagingTestHelper {

    private MessagingTestHelper() {
        // avoid instantiation
    }

    public static Message message(@Nonnull String qualifiedName, @Nullable Object payload) {
        return new GenericMessage(new MessageType(qualifiedName), payload);
    }

    public static Message message(@Nonnull Object payload) {
        return new GenericMessage(new MessageType(payload.getClass()), payload);
    }

    public static CommandMessage command(@Nonnull Object payload) {
        return new GenericCommandMessage(new MessageType(payload.getClass()), payload);
    }

    public static CommandMessage command(@Nonnull String qualifiedName, @Nonnull Object payload) {
        return new GenericCommandMessage(new MessageType(qualifiedName), payload);
    }

    public static CommandResultMessage commandResult(@Nonnull Object payload) {
        return new GenericCommandResultMessage(new MessageType(payload.getClass()), payload);
    }

    public static CommandResultMessage commandResult(@Nonnull String qualifiedName, @Nonnull Object payload) {
        return new GenericCommandResultMessage(new MessageType(qualifiedName), payload);
    }

    public static EventMessage event(@Nonnull Object payload) {
        return new GenericEventMessage(new MessageType(payload.getClass()), payload);
    }

    public static CommandResultMessage asCommandResultMessage(CommandMessage message) {
        var payload = message.payload();
        var messageType = payload != null ? new MessageType(payload.getClass()) : message.type();
        return new GenericCommandResultMessage(messageType, payload);
    }

    public static QueryMessage query(@Nonnull Object payload, @Nonnull Class<?> singleResponseType) {
        return new GenericQueryMessage(new MessageType(payload.getClass()),
                                       payload,
                                       ResponseTypes.instanceOf(singleResponseType));
    }

    public static QueryResponseMessage queryResponse(@Nonnull Object result) {
        return new GenericQueryResponseMessage(new MessageType(result.getClass()), result);
    }
}
