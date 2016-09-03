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

import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.pool.impl.GenericObjectPoolFactory;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandBusConnectorCommunicationException;
import org.axonframework.commandhandling.distributed.CommandCallbackRepository;
import org.axonframework.commandhandling.distributed.CommandCallbackWrapper;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The client to connect to the Websocket server. It is capable of scaling up the amount of clients in a pool if a
 * single connection is not sufficient for the load applied.
 *
 * @author Koen Lavooij
 */
@ClientEndpoint
public class WebsocketCommandBusConnectorClient extends Endpoint implements MessageHandler.Whole<ByteBuffer>, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketCommandBusConnectorClient.class);
    private static final int DEFAULT_SESSION_COUNT = 10;

    private final Serializer serializer = new XStreamSerializer();
    private final CommandCallbackRepository<String> repository = new CommandCallbackRepository<>();
    private final ObjectPool<Session> sessions;

    public WebsocketCommandBusConnectorClient(ClientSessionFactory clientSessionFactory) {
        this(clientSessionFactory, DEFAULT_SESSION_COUNT);
    }

    public WebsocketCommandBusConnectorClient(ClientSessionFactory clientSessionFactory, int sessionCount) {
        //Create the pool. Server side connections are bound to the commands in process. Therefore scaling down the
        //amount of connections results in losing callbacks of pending commands. We will therefore never scale down or
        //invalidate connections.
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        config.maxActive = sessionCount;
        config.maxIdle = sessionCount;
        config.maxWait = -1;
        config.minEvictableIdleTimeMillis = -1;
        config.minIdle = 0;
        config.numTestsPerEvictionRun = 0;
        config.softMinEvictableIdleTimeMillis = -1;
        config.testOnBorrow = true;
        config.testOnReturn = false;
        config.testWhileIdle = false;
        config.timeBetweenEvictionRunsMillis = -1;
        config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK;

        sessions = new GenericObjectPoolFactory<>(
                new PoolableObjectFactory<Session>() {
                    @Override
                    public Session makeObject() throws Exception {
                        return clientSessionFactory.createSession(WebsocketCommandBusConnectorClient.this);
                    }

                    @Override
                    public void destroyObject(Session obj) throws Exception {
                        if (obj.isOpen()) obj.close();
                    }

                    @Override
                    public boolean validateObject(Session obj) {
                        return obj.isOpen();
                    }

                    @Override
                    public void activateObject(Session obj) throws Exception {
                        //
                    }

                    @Override
                    public void passivateObject(Session obj) throws Exception {
                        //
                    }
                },
                config).createPool();
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        //log here?
    }

    @Override
    public void onError(Session session, Throwable cause) {
        LOGGER.warn("Connection error on session " + session.getId(), cause);
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        if (closeReason.getCloseCode() != CloseReason.CloseCodes.NORMAL_CLOSURE) {
            LOGGER.warn("Session closed because " + closeReason.getReasonPhrase());
        }
        try {
            sessions.invalidateObject(session);
            repository.cancelCallbacks(session.getId());
        } catch (Exception e) {
            LOGGER.error("Could not invalidate session", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void onMessage(ByteBuffer data) {
        //deserialize a message
        WebsocketResultMessage message = serializer.deserialize(new SimpleSerializedObject<>(data.array(), byte[].class,
                                                                                             serializer.typeForClass(WebsocketResultMessage.class)));

        //get the waiting callback
        CommandCallbackWrapper callbackWrapper = repository.fetchAndRemove(message.getCommandId());
        if (callbackWrapper != null) {
            if (message.getCause() != null) {
                callbackWrapper.fail(message.getCause());
            } else {
                callbackWrapper.success(message.getResult());
            }
        } else {
            LOGGER.error("Did not find callback for ID " + message.getCommandId());
        }
    }

    /**
     * Sends a command to the servewr
     * @param command The command to send
     * @param callback The callback to send Command results to. May be null if no callback is required
     * @param <C> The type of the Command
     * @param <R> The type of the Command result.
     */
    public <C, R> void send(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        ByteBuffer data = ByteBuffer.wrap(serializer.serialize(new WebsocketCommandMessage<>(command, callback != null),
                byte[].class).getData());

        try {
            Session session = sessions.borrowObject();
            try {
                LOGGER.debug("Using session " + session.getId() + " to send " + command.getCommandName());
                if (callback != null) {
                    repository.store(command.getIdentifier(), new CommandCallbackWrapper<>(session.getId(), command, callback));
                }
                session.getBasicRemote().sendBinary(data);
            } finally {
                sessions.returnObject(session);
            }
        } catch (Exception e) {
            LOGGER.error("Error sending command", e);
            if (callback != null) {
                callback.onFailure(command, new CommandBusConnectorCommunicationException(
                        "Failed to send command of type " + command.getCommandName() + " to remote", e));
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            sessions.close();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}

