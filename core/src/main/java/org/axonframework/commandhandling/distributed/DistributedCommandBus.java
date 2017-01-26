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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.MonitorAwareCallback;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.commandhandling.distributed.commandfilter.DenyCommandNameFilter;
import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

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

    /**
     * The initial load factor of this node when it is registered with the {@link CommandRouter}.
     */
    public static final int INITIAL_LOAD_FACTOR = 100;

    private static final String DISPATCH_ERROR_MESSAGE = "An error occurred while trying to dispatch a command "
            + "on the DistributedCommandBus";

    private final CommandRouter commandRouter;
    private final CommandBusConnector connector;
    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final AtomicReference<Predicate<CommandMessage<?>>> commandFilter = new AtomicReference<>(DenyAll.INSTANCE);
    private final MessageMonitor<? super CommandMessage<?>> messageMonitor;
    private volatile int loadFactor = INITIAL_LOAD_FACTOR;

    /**
     * Initializes the command bus with the given {@code commandRouter} and {@code connector}. The
     * {@code commandRouter} is used to determine the target node for each dispatched command. The {@code connector}
     * performs the actual transport of the message to the destination node.
     *
     * @param commandRouter the service registry that discovers the network of worker nodes
     * @param connector     the connector that connects the different command bus segments
     */
    public DistributedCommandBus(CommandRouter commandRouter, CommandBusConnector connector) {
        this(commandRouter, connector, NoOpMessageMonitor.INSTANCE);
    }

    /**
     * Initializes the command bus with the given {@code commandRouter}, {@code connector} and {@code messageMonitor}.
     * The {@code commandRouter} is used to determine the target node for each dispatched command.
     * The {@code connector} performs the actual transport of the message to the destination node.
     * The {@code messageMonitor} is used to monitor incoming messages and their execution result.
     *
     * @param commandRouter the service registry that discovers the network of worker nodes
     * @param connector     the connector that connects the different command bus segments
     * @param messageMonitor the message monitor to notify of incoming messages and their execution result
     */
    public DistributedCommandBus(CommandRouter commandRouter, CommandBusConnector connector, MessageMonitor<? super CommandMessage<?>> messageMonitor) {
        Assert.notNull(commandRouter, () -> "serviceRegistry may not be null");
        Assert.notNull(connector, () -> "connector may not be null");
        Assert.notNull(messageMonitor, () -> "messageMonitor may not be null");

        this.commandRouter = commandRouter;
        this.connector = connector;
        this.messageMonitor = messageMonitor;
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        if (NoOpMessageMonitor.INSTANCE.equals(messageMonitor)) {
            CommandMessage<? extends C> interceptedCommand = intercept(command);
            Member destination = commandRouter.findDestination(command)
                    .orElseThrow(() -> new CommandDispatchException("No node known to accept " + command.getCommandName()));
            try {
                connector.send(destination, interceptedCommand);
            } catch (Exception e) {
                destination.suspect();
                throw new CommandDispatchException(DISPATCH_ERROR_MESSAGE + ": " + e.getMessage(), e);
            }
        } else {
            dispatch(command, null);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws CommandDispatchException when an error occurs while dispatching the command to a segment
     */
    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        CommandMessage<? extends C> interceptedCommand = intercept(command);
        MonitorAwareCallback<? super C, R> monitorAwareCallback = new MonitorAwareCallback<>(callback, messageMonitor.onMessageIngested(command));

        Member destination = commandRouter.findDestination(command)
                .orElseThrow(() -> new CommandDispatchException("No node known to accept " + command.getCommandName()));
        try {
            connector.send(destination, interceptedCommand, monitorAwareCallback);
        } catch (Exception e) {
            destination.suspect();
            throw new CommandDispatchException(DISPATCH_ERROR_MESSAGE + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <C> CommandMessage<? extends C> intercept(CommandMessage<C> command) {
        CommandMessage<? extends C> interceptedCommand = command;
        for (MessageDispatchInterceptor<? super CommandMessage<?>> interceptor : dispatchInterceptors) {
            interceptedCommand = (CommandMessage<? extends C>) interceptor.handle(interceptedCommand);
        }
        return interceptedCommand;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * In the DistributedCommandBus, the handler is subscribed to the local segment only.
     */
    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        Registration reg = connector.subscribe(commandName, handler);
        updateFilter(commandFilter.get().or(new CommandNameFilter(commandName)));

        return () -> {
            updateFilter(commandFilter.get().and(new DenyCommandNameFilter(commandName)));
            return reg.cancel();
        };

    }

    private void updateFilter(Predicate<CommandMessage<?>> newFilter) {
        if (!commandFilter.getAndSet(newFilter).equals(newFilter)) {
            commandRouter.updateMembership(loadFactor, newFilter);
        }
    }

    /**
     * Returns the current load factor of this node.
     *
     * @return the current load factor
     */
    public int getLoadFactor() {
        return loadFactor;
    }

    /**
     * Updates the load factor of this node compared to other nodes registered with the {@link CommandRouter}.
     *
     * @param loadFactor the new load factor of this node
     */
    public void updateLoadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
        commandRouter.updateMembership(loadFactor, commandFilter.get());
    }

    /**
     * Registers the given list of dispatch interceptors to the command bus. All incoming commands will pass through
     * the interceptors at the given order before the command is dispatched toward the command handler.
     *
     * @param dispatchInterceptor The interceptors to invoke when commands are dispatched
     * @return handle to unregister the interceptor
     */
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }
}
