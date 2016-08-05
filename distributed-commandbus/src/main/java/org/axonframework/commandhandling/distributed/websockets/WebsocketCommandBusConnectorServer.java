/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import java.io.IOException;
import java.nio.ByteBuffer;

public class WebsocketCommandBusConnectorServer extends Endpoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketCommandBusConnectorServer.class);
    private final CommandBus localSegment;
    private final Serializer serializer;

    public WebsocketCommandBusConnectorServer(CommandBus localSegment, Serializer serializer) {
        if (localSegment == null) {
            throw new IllegalStateException("Trying to create a connection while no local segment is configured, " +
                    "supply one by calling WebsocketCommandBusConnectorServerConfigurator.setLocalSegment()");
        }
        this.serializer = serializer;
        this.localSegment = localSegment;
    }

    @Override
    public void onOpen(final Session session, EndpointConfig config) {
        session.setMaxIdleTimeout(1000);
        session.setMaxBinaryMessageBufferSize(WebsocketCommandBusConnector.MESSAGE_BUFFER_SIZE);
        session.setMaxTextMessageBufferSize(WebsocketCommandBusConnector.MESSAGE_BUFFER_SIZE);
        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
                receive(message, session);
            }
        });
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        super.onClose(session, closeReason);
    }

    @Override
    public void onError(Session session, Throwable cause) {
        LOGGER.warn("Connection error on session " + session.getId(), cause);
    }

    @OnMessage
    public <C, R> void receive(final ByteBuffer data, final Session session) {
        WebsocketCommandMessage message = serializer.deserialize(new SimpleSerializedObject<>(data.array(), byte[].class,
                                                                                              serializer.typeForClass(WebsocketCommandMessage.class)));
        if (message.isWithCallback()) {
            try {
               localSegment.dispatch(
                       message.getCommandMessage(),
                       new CommandCallback<C, R>() {
                           @Override
                           public void onSuccess(CommandMessage<? extends C> command, R result) {
                               sendResult(session, new WebsocketResultMessage<>(command.getIdentifier(), result, null));
                           }

                           @Override
                           public void onFailure(CommandMessage<? extends C> command, Throwable cause) {
                               sendResult(session, new WebsocketResultMessage<>(command.getIdentifier(), null, cause));
                           }
                       }
               );
           } catch (Exception e) {
               LOGGER.error("Error processing command " + message.getCommandMessage().getCommandName(), e);
               try {
                   sendResult(session, new WebsocketResultMessage<>(message.getCommandMessage().getIdentifier(), null, e));
               } catch (Exception e1) {
                   LOGGER.error("Could not send result to remote ", e1);
               }
           }
        } else {
            try {
                localSegment.dispatch(message.getCommandMessage());
            } catch (Exception e) {
                LOGGER.error("Error processing command " + message.getCommandMessage().getCommandName(), e);
            }
        }
    }

    private <R> void sendResult(Session session, WebsocketResultMessage<R> obj) {
        //serialize the message outside the synchronized block
        ByteBuffer data = ByteBuffer.wrap(serializer.serialize(obj, byte[].class).getData());
        try {
            //prevent sending mutiple responses in parallel, otherwise messages may get lost
            synchronized (this) {
                session.getBasicRemote().sendBinary(data);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

