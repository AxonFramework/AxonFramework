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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementation of a {@link CommandBus} that is aware of multiple instances of a CommandBus working together to
 * spread load. Each "physical" CommandBus instance is considered a "segment" of a conceptual distributed CommandBus.
 * <p/>
 * The DistributedCommandBus relies on a {@link CommandBusConnector} to dispatch commands and replies to different
 * segments of the CommandBus. Depending on the implementation used, each segment may run in a different JVM.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DistributedCommandBus implements CommandBus {

    private static final String DISPATCH_ERROR_MESSAGE = "An error occurred while trying to dispatch a command "
            + "on the DistributedCommandBus";
    private static final Logger logger = LoggerFactory.getLogger(DistributedCommandBus.class);

    private final RoutingStrategy routingStrategy;
    private final CommandBusConnector connector;
    private final List<CommandDispatchInterceptor> dispatchInterceptors = new CopyOnWriteArrayList<CommandDispatchInterceptor>();

    /**
     * Initializes the command bus with the given <code>connector</code> and an {@link AnnotationRoutingStrategy}.
     *
     * @param connector the connector that connects the different command bus segments
     */
    public DistributedCommandBus(CommandBusConnector connector) {
        this(connector, new AnnotationRoutingStrategy());
    }

    /**
     * Initializes the command bus with the given <code>connector</code> and <code>routingStrategy</code>. The
     * <code>routingStrategy</code> is used to calculate a routing key for each dispatched command. For a given
     * configuration of segments, commands resulting in the same routing key are routed to the same segment.
     *
     * @param connector       the connector that connects the different command bus segments
     * @param routingStrategy the RoutingStrategy to define routing keys for each command
     */
    public DistributedCommandBus(CommandBusConnector connector, RoutingStrategy routingStrategy) {
        this.connector = connector;
        this.routingStrategy = routingStrategy;
    }

    /**
     * {@inheritDoc}
     *
     * @throws CommandDispatchException when an error occurs while dispatching the command to a segment
     */
    @Override
    public void dispatch(CommandMessage<?> command) {
        command = intercept(command);
        String routingKey = routingStrategy.getRoutingKey(command);
        try {
            connector.send(routingKey, command);
        } catch (Exception e) {
            logger.error(DISPATCH_ERROR_MESSAGE, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws CommandDispatchException when an error occurs while dispatching the command to a segment
     */
    @Override
    public <R> void dispatch(CommandMessage<?> command, CommandCallback<R> callback) {
        command = intercept(command);
        String routingKey = routingStrategy.getRoutingKey(command);
        try {
            connector.send(routingKey, command, callback);
        } catch (Exception e) {
            callback.onFailure(new CommandDispatchException(DISPATCH_ERROR_MESSAGE, e));
        }
    }

    private CommandMessage<?> intercept(CommandMessage<?> command) {
        for (CommandDispatchInterceptor interceptor : dispatchInterceptors) {
            command = interceptor.handle(command);
        }
        return command;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * In the DistributedCommandBus, the handler is subscribed to the local segment only.
     */
    @Override
    public <C> void subscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        connector.subscribe(commandType, handler);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * In the DistributedCommandBus, the handler is unsubscribed from the local segment only.
     */
    @Override
    public <C> boolean unsubscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        return connector.unsubscribe(commandType, handler);
    }

    /**
     * Sets the interceptors that intercept commands just prior to dispatching them.
     * <p/>
     * This operation is only guaranteed to be thread safe if no commands are dispatched during the invocation of this
     * method. Doing so may result in commands not being intercepted at all while replacing the interceptors. Once this
     * operation returns, all commands are guaranteed to be processed by the given interceptors.
     *
     * @param dispatchInterceptors The interceptors to intercepts commands with
     */
    public void setCommandDispatchInterceptors(Collection<CommandDispatchInterceptor> dispatchInterceptors) {
        this.dispatchInterceptors.clear();
        this.dispatchInterceptors.addAll(dispatchInterceptors);
    }
}
