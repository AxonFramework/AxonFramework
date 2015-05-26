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
import org.axonframework.serializer.Serializer;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal class used used by JGroupsConnector. For internal use only. Pulled outside to allow for seamless unit testing
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ReplyingCallback implements CommandCallback<Object> {

    private final Message msg;
    private final CommandMessage commandMessage;
    private final JChannel channel;
    private Serializer serializer;

    private static final Logger logger = LoggerFactory.getLogger(ReplyingCallback.class);

    public ReplyingCallback(JChannel channel, Message msg, CommandMessage commandMessage, Serializer serializer) {
        this.msg = msg;
        this.commandMessage = commandMessage;
        this.channel = channel;
        this.serializer = serializer;
    }

    @Override
    public void onSuccess(Object result) {
        try {
            channel.send(msg.getSrc(), new ReplyMessage(commandMessage.getIdentifier(),
                    result,
                    null, serializer));
        } catch (Exception e) {
            logger.error("Unable to send reply to command [name: {}, id: {}]. ",
                    new Object[]{commandMessage.getCommandName(),
                            commandMessage.getIdentifier(),
                            e});
            throw new CommandResponseProcessingFailedException(String.format("An error occurred while attempting to process command response of type : %s", result.getClass().getName()),e);
        }
    }

    @Override
    public void onFailure(Throwable cause) {
        try {
            channel.send(msg.getSrc(), new ReplyMessage(commandMessage.getIdentifier(),
                    null,
                    cause, serializer));
        } catch (Exception e) {
            logger.error("Unable to send reply:", e);
            throw new CommandResponseProcessingFailedException(String.format("An error occurred while attempting to process command exception response of type : %s", e.getClass().getName()),e);
        }
    }
}
