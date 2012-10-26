/*
 * Copyright (c) 2010-2012. Axon Framework
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
    private final T command;
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
    public static CommandMessage asCommandMessage(Object command) {
        if (CommandMessage.class.isInstance(command)) {
            return (CommandMessage) command;
        }
        return new GenericCommandMessage<Object>(command);
    }

    public static CommandMessage asCommandMessage(Object command, Map<String, ?> newMetaData) {
        if (CommandMessage.class.isInstance(command)) {
            return (CommandMessage) command;
        }
        return new GenericCommandMessage<Object>(command, newMetaData);
    }    
    
    /**
     * Create a CommandMessage with the given <code>command</code> as payload and empty metaData
     *
     * @param command the payload for the Message
     */
    public GenericCommandMessage(T command) {
        this(command, MetaData.emptyInstance());
    }

    /**
     * Create a CommandMessage with the given <code>command</code> as payload.
     *
     * @param command     the payload for the Message
     * @param newMetaData The meta data for this message
     */
    public GenericCommandMessage(T command, Map<String, ?> newMetaData) {
        this.command = command;
        this.metaData = MetaData.from(newMetaData);
        this.identifier = IdentifierFactory.getInstance().generateIdentifier();
    }

    /**
     * Create a CommandMessage with the given <code>command</code> as payload and a custom chosen
     * <code>identifier</code>. Use this constructor to reconstruct instances of existing command messages, which have
     * already been assigned an identifier.
     *
     * @param identifier  the unique identifier of this message
     * @param command     the payload for the Message
     * @param newMetaData The meta data for this message
     */
    public GenericCommandMessage(String identifier, T command, Map<String, ?> newMetaData) {
        this.identifier = identifier;
        this.command = command;
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
        this.command = original.getPayload();
        this.metaData = MetaData.from(metaData);
    }

    @Override
    public MetaData getMetaData() {
        return metaData;
    }

    @Override
    public T getPayload() {
        return command;
    }

    @Override
    public Class getPayloadType() {
        return command.getClass();
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
        return new GenericCommandMessage<T>(this, newMetaData);
    }

    @Override
    public GenericCommandMessage<T> andMetaData(Map<String, ?> additionalMetaData) {
        if (additionalMetaData.isEmpty()) {
            return this;
        }
        return new GenericCommandMessage<T>(this, metaData.mergedWith(additionalMetaData));
    }
}
