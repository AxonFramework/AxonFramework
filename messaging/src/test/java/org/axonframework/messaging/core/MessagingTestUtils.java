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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.GenericQueryResponseMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;

/**
 * A utility class providing static factory methods for creating various types of {@link Message Message s} commonly
 * used in testing scenarios.
 *
 * @author Simon Zambrovski
 */
public class MessagingTestUtils {

    /**
     * Creates a {@link Message} with the specified qualified name and payload.
     *
     * @param qualifiedName The fully qualified name to use for the message type.
     * @param payload       The payload object to include in the message, may be null.
     * @return A new {@link Message} instance with the specified type and payload.
     */
    public static Message message(@Nonnull String qualifiedName, @Nullable Object payload) {
        return new GenericMessage(new MessageType(qualifiedName), payload);
    }

    /**
     * Creates a {@link Message} with a message type derived from the payload's class.
     *
     * @param payload The payload object to include in the message, used to determine the message type.
     * @return A new {@link Message} instance with a type based on the payload's class.
     */
    public static Message message(@Nonnull Object payload) {
        return new GenericMessage(new MessageType(payload.getClass()), payload);
    }

    /**
     * Creates a {@link CommandMessage} with a message type derived from the payload's class.
     *
     * @param payload The payload object to include in the command message.
     * @return A new {@link CommandMessage} instance with a type based on the payload's class.
     */
    public static CommandMessage command(@Nonnull Object payload) {
        return new GenericCommandMessage(new MessageType(payload.getClass()), payload);
    }

    /**
     * Creates a {@link CommandMessage} with the specified qualified name and payload.
     *
     * @param qualifiedName The fully qualified name to use for the message type.
     * @param payload       The payload object to include in the command message.
     * @return A new {@link CommandMessage} instance with the specified type and payload.
     */
    public static CommandMessage command(@Nonnull String qualifiedName, @Nonnull Object payload) {
        return new GenericCommandMessage(new MessageType(qualifiedName), payload);
    }

    /**
     * Creates a {@link CommandResultMessage} with a message type derived from the payload's class.
     *
     * @param payload The payload object to include in the command result message.
     * @return A new {@link CommandResultMessage} instance with a type based on the payload's class.
     */
    public static CommandResultMessage commandResult(@Nonnull Object payload) {
        return new GenericCommandResultMessage(new MessageType(payload.getClass()), payload);
    }

    /**
     * Creates a {@link CommandResultMessage} with the specified qualified name and payload.
     *
     * @param qualifiedName The fully qualified name to use for the message type.
     * @param payload       The payload object to include in the command result message.
     * @return A new {@link CommandResultMessage} instance with the specified type and payload.
     */
    public static CommandResultMessage commandResult(@Nonnull String qualifiedName, @Nonnull Object payload) {
        return new GenericCommandResultMessage(new MessageType(qualifiedName), payload);
    }

    /**
     * Converts the given {@code command} into a {@link CommandResultMessage}, preserving the payload and determining
     * the message type from the payload if available, otherwise using the original message type.
     *
     * @param command The command message to convert into a {@link CommandResultMessage}.
     * @return A new {@link CommandResultMessage} with the same payload and appropriate message type.
     */
    public static CommandResultMessage asCommandResultMessage(CommandMessage command) {
        var payload = command.payload();
        var messageType = payload != null ? new MessageType(payload.getClass()) : command.type();
        return new GenericCommandResultMessage(messageType, payload);
    }

    /**
     * Creates an {@link EventMessage} with a message type derived from the payload's class.
     *
     * @param payload The payload object to include in the event message.
     * @return A new {@link EventMessage} instance with a type based on the payload's class.
     */
    public static EventMessage event(@Nonnull Object payload) {
        return new GenericEventMessage(new MessageType(payload.getClass()), payload);
    }

    /**
     * Creates a {@link QueryMessage} with a message type derived from the payload's class.
     *
     * @param payload      The payload object to include in the query message.
     * @return A new {@link QueryMessage} instance configured for a single response type.
     */
    public static QueryMessage query(@Nonnull Object payload) {
        return new GenericQueryMessage(new MessageType(payload.getClass()), payload);
    }

    /**
     * Creates a {@link QueryResponseMessage} with a message type derived from the result's class.
     *
     * @param result The result object to include in the query response message.
     * @return A new {@link QueryResponseMessage} instance with a type based on the result's class.
     */
    public static QueryResponseMessage queryResponse(@Nonnull Object result) {
        return new GenericQueryResponseMessage(new MessageType(result.getClass()), result);
    }

    private MessagingTestUtils() {
        // Utility class
    }
}
