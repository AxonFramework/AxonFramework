/*
 * Copyright (c) 2010-2014. Axon Framework
 *
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

package org.axonframework.commandhandling;

import org.axonframework.domain.IdentifierFactory;
import org.axonframework.domain.MetaData;

import java.util.Map;

/**
 * Implementation of the CommandMessage that takes all properties as constructor parameters.
 *
 * @param <T> The type of payload contained in this Message
 * @author Allard Buijze
 * @since 2.0
 */
public class GenericCommandMessage<T> implements CommandMessage<T> {

    private static final long serialVersionUID = 8754588074137370013L;

    private final String identifier;
    private final String commandName;
    private final T payload;
    private final MetaData metaData;

    /**
     * Returns the given command as a CommandMessage. If <code>command</code> already implements CommandMessage, it is
     * returned as-is. Otherwise, the given <code>command</code> is wrapped into a GenericCommandMessage as its
     * payload.
     *
     * @param command the command to wrap as CommandMessage
     * @return a CommandMessage containing given <code>command</code> as payload, or <code>command</code> if it already
     *         implements CommandMessage.
     */
    @SuppressWarnings("unchecked")
    public static <C> CommandMessage<C> asCommandMessage(Object command) {
        if (CommandMessage.class.isInstance(command)) {
            return (CommandMessage<C>) command;
        }
        return new GenericCommandMessage<>((C) command);
    }

    /**
     * Create a CommandMessage with the given <code>command</code> as payload and empty metaData
     *
     * @param payload the payload for the Message
     */
    public GenericCommandMessage(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    /**
     * Create a CommandMessage with the given <code>command</code> as payload.
     *
     * @param payload     the payload for the Message
     * @param newMetaData The meta data for this message
     */
    public GenericCommandMessage(T payload, Map<String, ?> newMetaData) {
        this(payload.getClass().getName(), payload, newMetaData);
    }

    /**
     * Create a CommandMessage with the given <code>command</code> as payload.
     *
     * @param commandName The name of the command
     * @param payload     the payload for the Message
     * @param newMetaData The meta data for this message
     */
    public GenericCommandMessage(String commandName, T payload, Map<String, ?> newMetaData) {
        this.commandName = commandName;
        this.payload = payload;
        this.metaData = MetaData.from(newMetaData);
        this.identifier = IdentifierFactory.getInstance().generateIdentifier();
    }


    /**
     * Create a CommandMessage with the given <code>command</code> as payload and a custom chosen
     * <code>identifier</code>. Use this constructor to reconstruct instances of existing command messages, which have
     * already been assigned an identifier.
     *
     * @param identifier  the unique identifier of this message
     * @param commandName The name of the command
     * @param payload     the payload for the Message
     * @param newMetaData The meta data for this message (<code>null</code> results in empty meta data)
     */
    public GenericCommandMessage(String identifier, String commandName, T payload, Map<String, ?> newMetaData) {
        this.identifier = identifier;
        this.commandName = commandName;
        this.payload = payload;
        this.metaData = MetaData.from(newMetaData);
    }

    /**
     * Copy constructor that allows creation of a new GenericCommandMessage with modified metaData. All information
     * from the <code>original</code> is copied, except for the metaData.
     *
     * @param original The original message
     * @param metaData The MetaData for the new message
     */
    protected GenericCommandMessage(GenericCommandMessage<T> original, Map<String, ?> metaData) {
        this.identifier = original.getIdentifier();
        this.commandName = original.getCommandName();
        this.payload = original.getPayload();
        this.metaData = MetaData.from(metaData);
    }

    @Override
    public String getCommandName() {
        return commandName;
    }

    @Override
    public MetaData getMetaData() {
        return metaData;
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public Class getPayloadType() {
        return payload.getClass();
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public GenericCommandMessage<T> withMetaData(Map<String, ?> newMetaData) {
        if (getMetaData().equals(newMetaData)) {
            return this;
        }
        return new GenericCommandMessage<>(this, newMetaData);
    }

    @Override
    public GenericCommandMessage<T> andMetaData(Map<String, ?> additionalMetaData) {
        if (additionalMetaData.isEmpty()) {
            return this;
        }
        return new GenericCommandMessage<>(this, metaData.mergedWith(additionalMetaData));
    }
}
