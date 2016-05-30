/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.commandhandling.distributed.jgroups.support.callbacks;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.jgroups.CommandResponseProcessingFailedException;
import org.axonframework.commandhandling.distributed.jgroups.ReplyMessage;
import org.axonframework.serialization.Serializer;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback implementation that forwards the callback invocation as a reply to an incoming message.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ReplyingCallback implements CommandCallback<Object, Object> {

    private final JChannel channel;
    private final Serializer serializer;

    private static final Logger logger = LoggerFactory.getLogger(ReplyingCallback.class);
    private final Address address;

    /**
     * Initialize the callback to send a reply to given <code>address</code> using the given <code>channel</code>.
     * The given <code>serializer</code> is used to serialize the reply message.
     *
     * @param channel    The channel to send the reply on
     * @param address    The destination for the reply message
     * @param serializer The serializer to serialize the reply with
     */
    public ReplyingCallback(JChannel channel, Address address, Serializer serializer) {
        this.address = address;
        this.channel = channel;
        this.serializer = serializer;
    }

    @Override
    public void onSuccess(CommandMessage<?> commandMessage, Object result) {
        try {
            channel.send(address, new ReplyMessage(commandMessage.getIdentifier(),
                                                   result,
                                                   null, serializer));
        } catch (Exception e) {
            logger.error("Unable to send reply to command [name: {}, id: {}]. ",
                         commandMessage.getCommandName(), commandMessage.getIdentifier(), e);
            throw new CommandResponseProcessingFailedException(String.format(
                    "An error occurred while attempting to process command response of type : %s, Exception Message: %s",
                    result.getClass().getName(), e.getMessage()), e);
        }
    }

    @Override
    public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
        try {
            channel.send(address, new ReplyMessage(commandMessage.getIdentifier(),
                                                   null,
                                                   cause, serializer));
        } catch (Exception e) {
            logger.error("Unable to send reply:", e);
            //Not capturing the causative exception while throwing - the causative exception may not be serializable and this may cause the command bus to hang.
            throw new CommandResponseProcessingFailedException(String.format(
                    "An error occurred while attempting to process command exception response of type : %s, Exception Message:: %s",
                    e.getClass().getName(),
                    e.getMessage()));
        }
    }
}
