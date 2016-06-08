/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.messaging.metadata.MetaData;

import java.util.Map;

/**
 * @author Rene de Waele
 */
public class GenericCommandMessage<T> extends MessageDecorator<T> implements CommandMessage<T> {
    private final String commandName;

    @SuppressWarnings("unchecked")
    public static <C> CommandMessage<C> asCommandMessage(Object command) {
        if (CommandMessage.class.isInstance(command)) {
            return (CommandMessage<C>) command;
        }
        return new GenericCommandMessage<>((C) command, MetaData.emptyInstance());
    }

    public GenericCommandMessage(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    public GenericCommandMessage(T payload, Map<String, ?> metaData) {
        this(new GenericMessage<>(payload, metaData), payload.getClass().getName());
    }

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
}
