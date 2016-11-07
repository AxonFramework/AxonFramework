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

/**
 * Websocket {@link Endpoint} that receives serialized commands and dispatches these on a provided command bus. Also
 * sends back the result of a command if the sender is interested.
 */
public class WebsocketCommandBusConnectorServer extends Endpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketCommandBusConnectorServer.class);

    private final Serializer serializer;
    private final CommandBus commandBus;

    /**
     * Initializes a WebsocketCommandBusConnectorServer with given {@code commandBus} and {@code serializer}.
     *
     * @param commandBus used to dispatch received commands
     * @param serializer used to deserialize received commands and serialize command results
     */
    public WebsocketCommandBusConnectorServer(CommandBus commandBus, Serializer serializer) {
        this.serializer = serializer;
        this.commandBus = commandBus;
    }

    @Override
    public void onOpen(final Session session, EndpointConfig config) {
        session.setMaxIdleTimeout(1000);
        session.setMaxBinaryMessageBufferSize(WebsocketCommandBusConnector.MESSAGE_BUFFER_SIZE);
        session.setMaxTextMessageBufferSize(WebsocketCommandBusConnector.MESSAGE_BUFFER_SIZE);
        session.addMessageHandler((MessageHandler.Whole<ByteBuffer>) message -> receive(message, session));
    }

    @Override
    public void onError(Session session, Throwable cause) {
        LOGGER.warn("Connection error on session " + session.getId(), cause);
    }

    /**
     * Method invoked when new data is received.
     *
     * @param data    byte buffer containing the data
     * @param session the web socket session
     * @param <C> command type
     * @param <R> command result type
     */
    @OnMessage
    public <C, R> void receive(final ByteBuffer data, final Session session) {
        WebsocketCommandMessage message = serializer.deserialize(
                new SimpleSerializedObject<>(data.array(), byte[].class,
                                             serializer.typeForClass(WebsocketCommandMessage.class)));
        if (message.isWithCallback()) {
            try {
                commandBus.dispatch(message.getCommandMessage(), new CommandCallback<C, R>() {
                    @Override
                    public void onSuccess(CommandMessage<? extends C> command, R result) {
                        sendResult(session, new WebsocketResultMessage<>(command.getIdentifier(), result, null));
                    }

                    @Override
                    public void onFailure(CommandMessage<? extends C> command, Throwable cause) {
                        sendResult(session, new WebsocketResultMessage<>(command.getIdentifier(), null, cause));
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Error processing command " + message.getCommandMessage().getCommandName(), e);
                try {
                    sendResult(session,
                               new WebsocketResultMessage<>(message.getCommandMessage().getIdentifier(), null, e));
                } catch (Exception e1) {
                    LOGGER.error("Could not send result to remote ", e1);
                }
            }
        } else {
            try {
                commandBus.dispatch(message.getCommandMessage());
            } catch (Exception e) {
                LOGGER.error("Error processing command " + message.getCommandMessage().getCommandName(), e);
            }
        }
    }

    private <R> void sendResult(Session session, WebsocketResultMessage<R> obj) {
        ByteBuffer data = ByteBuffer.wrap(serializer.serialize(obj, byte[].class).getData());
        try {
            //prevent sending multiple responses in parallel, otherwise messages may get lost
            synchronized (this) {
                session.getBasicRemote().sendBinary(data);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
