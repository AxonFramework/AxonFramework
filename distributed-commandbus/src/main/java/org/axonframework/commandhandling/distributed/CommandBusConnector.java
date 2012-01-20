package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;

/**
 * Interface describing the component that remotely connects multiple CommandBus instances.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface CommandBusConnector {

    /**
     * Sends the given <code>command</code> to the node assigned to handle messages with the given
     * <code>routingKey</code>. The sender does not expect a reply.
     * <p/>
     * If this method throws an exception, the sender is guaranteed that the destination of the command did not receive
     * it. If the method returns normally, the actual implementation of the connector defines the delivery guarantees.
     * <p/>
     * Connectors route the commands based on the given <code>routingKey</code>. Using the same <code>routingKey</code>
     * will result in the command being sent to the same member. Each message must be sent to <em>exactly one
     * member</em>.
     *
     * @param routingKey The key describing the routing requirements of this command. Generally, commands with the same
     *                   routingKey will be sent to the same destination.
     * @param command    The command to send to the (remote) member
     * @throws Exception when an error occurs before or during the sending of the message
     */
    void send(String routingKey, CommandMessage<?> command) throws Exception;

    /**
     * Sends the given <code>command</code> to the node assigned to handle messages with the given
     * <code>routingKey</code>. The sender expect a reply, and will be notified of the result in the given
     * <code>callback</code>.
     * <p/>
     * If this method throws an exception, the sender is guaranteed that the destination of the command did not receive
     * it. If the method returns normally, the actual implementation of the connector defines the delivery guarantees.
     * Implementations <em>should</em> always invoke the callback with an outcome.
     * <p/>
     * If a member's connection was lost, and the result of the command is unclear, the {@link
     * CommandCallback#onFailure(Throwable)} method is invoked with a {@link RemoteCommandHandlingException} describing
     * the failed connection. A client may choose to resend a command.
     * <p/>
     * Connectors route the commands based on the given <code>routingKey</code>. Using the same <code>routingKey</code>
     * will result in the command being sent to the same member.
     *
     * @param routingKey The key describing the routing requirements of this command. Generally, commands with the same
     *                   routingKey will be sent to the same destination.
     * @param command    The command to send to the (remote) member
     * @param callback   The callback on which result notifications are sent
     * @param <R>        The type of object expected as return value in the callback
     * @throws Exception when an error occurs before or during the sending of the message
     */
    <R> void send(String routingKey, CommandMessage<?> command, CommandCallback<R> callback) throws Exception;

    /**
     * The CommandBus instance on which local messages should be dispatched.
     * <p/>
     * This is generally <em>not</em> a Distributed Command Bus, unless this connector serves as a proxy between to
     * distributed buses.
     *
     * @return the CommandBus instance on which local messages should be dispatched.
     */
    CommandBus getLocalSegment();
}
