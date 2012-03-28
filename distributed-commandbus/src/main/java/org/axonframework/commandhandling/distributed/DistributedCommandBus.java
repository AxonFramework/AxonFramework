package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;

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

    private final RoutingStrategy routingStrategy;
    private final CommandBusConnector connector;

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
        String routingKey = routingStrategy.getRoutingKey(command);
        try {
            connector.send(routingKey, command);
        } catch (Exception e) {
            throw new CommandDispatchException(DISPATCH_ERROR_MESSAGE, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws CommandDispatchException when an error occurs while dispatching the command to a segment
     */
    @Override
    public <R> void dispatch(CommandMessage<?> command, CommandCallback<R> callback) {
        String routingKey = routingStrategy.getRoutingKey(command);
        try {
            connector.send(routingKey, command, callback);
        } catch (Exception e) {
            throw new CommandDispatchException(DISPATCH_ERROR_MESSAGE, e);
        }
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
}
