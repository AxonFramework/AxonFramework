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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.commandhandling.distributed.registry.ServiceMember;
import org.axonframework.commandhandling.distributed.registry.ServiceRegistry;
import org.axonframework.commandhandling.distributed.registry.ServiceRegistryException;
import org.axonframework.commandhandling.distributed.registry.ServiceRegistryListener;
import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
public class DistributedCommandBus<D> implements CommandBus, ServiceRegistryListener<D> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedCommandBus.class);
    private static final String DISPATCH_ERROR_MESSAGE = "An error occurred while trying to dispatch a command "
            + "on the DistributedCommandBus";
    public static final int INITIAL_LOAD_FACTOR = 100;

    private final CommandBus localCommandBus;
    private final RoutingStrategy routingStrategy;
    private final ServiceRegistry<D> serviceRegistry;
    private final CommandBusConnector<D> connector;
    private final List<MessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

    private ConsistentHash<ServiceMember<D>> consistentHash = new ConsistentHash<>();
    private Predicate<CommandMessage> commandFilter = DenyAll.INSTANCE;
    private int loadFactor = INITIAL_LOAD_FACTOR;

    /**
     * Initializes the command bus with the given <code>connector</code> and an {@link AnnotationRoutingStrategy}.
     *
     * @param localCommandBus the local commandbus to put messages on
     * @param serviceRegistry the service registry that discovers the network of worker nodes
     * @param connector       the connector that connects the different command bus segments
     */
    public DistributedCommandBus(CommandBus localCommandBus, ServiceRegistry<D> serviceRegistry,
                                 CommandBusConnector<D> connector) {
        this(localCommandBus, serviceRegistry, connector, new AnnotationRoutingStrategy());
    }

    /**
     * Initializes the command bus with the given <code>connector</code> and <code>routingStrategy</code>. The
     * <code>routingStrategy</code> is used to calculate a routing key for each dispatched command. For a given
     * configuration of segments, commands resulting in the same routing key are routed to the same segment.
     * @param localCommandBus the local commandbus to put messages on
     * @param serviceRegistry the service registry that discovers the network of worker nodes
     * @param connector       the connector that connects the different command bus segments
     * @param routingStrategy the RoutingStrategy to define routing keys for each command
     */
    public DistributedCommandBus(CommandBus localCommandBus, ServiceRegistry<D> serviceRegistry,
                                 CommandBusConnector<D> connector, RoutingStrategy routingStrategy) {
        this.localCommandBus = localCommandBus;
        Assert.notNull(serviceRegistry, "serviceRegistry may not be null");
        Assert.notNull(connector, "connector may not be null");
        Assert.notNull(routingStrategy, "routingStrategy may not be null");

        this.serviceRegistry = serviceRegistry;
        serviceRegistry.addListener(this);
        this.connector = connector;
        this.routingStrategy = routingStrategy;
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        CommandMessage<? extends C> interceptedCommand = intercept(command);
        String routingKey = routingStrategy.getRoutingKey(interceptedCommand);
        D destination = getMember(routingKey, command).getIdentifier();
        try {
            connector.send(destination, interceptedCommand);
        } catch (Exception e) {
            throw new CommandDispatchException(DISPATCH_ERROR_MESSAGE + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void updateMembers(Set<ServiceMember<D>> members) {
        consistentHash = consistentHash.rebuildWith(members);
        connector.updateMembers(members.stream().map(ServiceMember::getIdentifier).collect(Collectors.toSet()));
    }

    /**
     * Gets a member by the routing key from the network
     * @param routingKey        The routing key to get a member for
     * @param commandMessage    The command message to get a member for
     * @return                  The member
     */
    private ServiceMember<D> getMember(String routingKey, CommandMessage<?> commandMessage) {
        return consistentHash.getMember(routingKey, commandMessage);
    }

    /**
     * {@inheritDoc}
     *
     * @throws CommandDispatchException when an error occurs while dispatching the command to a segment
     */
    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        CommandMessage<? extends C> interceptedCommand = intercept(command);
        String routingKey = routingStrategy.getRoutingKey(interceptedCommand);
        ServiceMember<D> member = getMember(routingKey, command);
        if (member == null) {
            throw new CommandDispatchException("No node known to accept " + command.getCommandName());
        }
        D target = member.getIdentifier();
        try {
            if (target.equals(connector.getLocalEndpoint())) {
                localCommandBus.dispatch(command, callback);
            } else {
                connector.send(target, interceptedCommand, callback);
            }

        } catch (Exception e) {
            throw new CommandDispatchException(DISPATCH_ERROR_MESSAGE + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <C> CommandMessage<? extends C> intercept(CommandMessage<C> command) {
        CommandMessage<? extends C> interceptedCommand = command;
        for (MessageDispatchInterceptor<CommandMessage<?>> interceptor : dispatchInterceptors) {
            interceptedCommand = (CommandMessage<? extends C>) interceptor.handle(interceptedCommand);
        }
        return command;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * In the DistributedCommandBus, the handler is subscribed to the local segment only.
     */
    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        updateFilter(commandFilter.or(new CommandNameFilter(commandName)));
        localCommandBus.subscribe(commandName, handler);

        return () -> {
            updateFilter(commandFilter.and(new CommandNameFilter(commandName).negate()));
            return true;
        };

    }

    private void updateFilter(Predicate<CommandMessage> newFilter) {
        if (!commandFilter.equals(newFilter)) {
            commandFilter = newFilter;
            try {
                serviceRegistry.publish(connector.getLocalEndpoint(), connector.getLoadFactor(), newFilter);
            } catch (ServiceRegistryException e) {
                LOGGER.error("Could not publish endpoint", e);
            }
        }
    }

    public int getLoadFactor() {
        return loadFactor;
    }

    public void setLoadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
        try {
            serviceRegistry.publish(connector.getLocalEndpoint(), loadFactor, commandFilter);
        } catch (ServiceRegistryException e) {
            LOGGER.error("Could not publish endpoint", e);
        }
    }

    /**
     * Sets the interceptors that intercept commands just prior to dispatching them.
     * <p/>
     * This operation is only guaranteed to be thread safe if no commands are dispatched during the invocation of this
     * method. Doing so may result in commands not being intercepted at all while replacing the interceptors. Once this
     * operation returns, all commands are guaranteed to be processed by the given interceptors.
     *
     * @param newDispatchInterceptors The interceptors to intercepts commands with
     */
    public void setCommandDispatchInterceptors(Collection<MessageDispatchInterceptor<CommandMessage<?>>> newDispatchInterceptors) {
        this.dispatchInterceptors.clear();
        this.dispatchInterceptors.addAll(newDispatchInterceptors);
    }
}
