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

package org.axonframework.commandhandling;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MetaData;

import java.util.Map;

/**
 * Implementation of the CommandMessage that takes all properties as constructor parameters.
 *
 * @param <T> The type of payload contained in this Message
 * @author Allard Buijze
 * @since 2.0
 */
public class GenericCommandMessage<T> extends MessageDecorator<T> implements CommandMessage<T> {
    private static final long serialVersionUID = 3282528436414939876L;
    private final String commandName;

    /**
     * Returns the given command as a CommandMessage. If {@code command} already implements CommandMessage, it is
     * returned as-is. Otherwise, the given {@code command} is wrapped into a GenericCommandMessage as its
     * payload.
     *
     * @param command the command to wrap as CommandMessage
     * @return a CommandMessage containing given {@code command} as payload, or {@code command} if it already implements
     * CommandMessage.
     */
    @SuppressWarnings("unchecked")
    public static <C> CommandMessage<C> asCommandMessage(Object command) {
        if (CommandMessage.class.isInstance(command)) {
            return (CommandMessage<C>) command;
        }
        return new GenericCommandMessage<>((C) command, MetaData.emptyInstance());
    }

    /**
     * Create a CommandMessage with the given {@code command} as payload and empty metaData
     *
     * @param payload the payload for the Message
     */
    public GenericCommandMessage(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    /**
     * Create a CommandMessage with the given {@code command} as payload.
     *
     * @param payload  the payload for the Message
     * @param metaData The meta data for this message
     */
    public GenericCommandMessage(T payload, Map<String, ?> metaData) {
        this(new GenericMessage<>(payload, metaData), payload.getClass().getName());
    }

    /**
     * Create a CommandMessage from the given {@code delegate} message containing payload, metadata and message
     * identifier, and the given {@code commandName}.
     *
     * @param delegate    the delegate message
     * @param commandName The name of the command
     */
    public GenericCommandMessage(Message<T> delegate, String commandName) {
        super(delegate);
        this.commandName = commandName;
    }

    @Override
    public String getCommandName() {
        return commandName;
    }

    @Override
    public GenericCommandMessage<T> withMetaData(Map<String, ?> metaData) {
        return new GenericCommandMessage<>(getDelegate().withMetaData(metaData), commandName);
    }

    @Override
    public GenericCommandMessage<T> andMetaData(Map<String, ?> metaData) {
        return new GenericCommandMessage<>(getDelegate().andMetaData(metaData), commandName);
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append(", commandName='")
                     .append(getCommandName())
                     .append('\'');
    }

    @Override
    protected String describeType() {
        return "GenericCommandMessage";
    }
}
