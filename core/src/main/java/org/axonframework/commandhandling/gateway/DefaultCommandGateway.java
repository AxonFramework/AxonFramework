package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.commandhandling.callbacks.NoOpCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;

/**
 * Default implementation of the CommandGateway interface. It allow configuration of a {@link RetryScheduler} and
 * {@link CommandDispatchInterceptor CommandDispatchInterceptors}. The Retry Scheduler allows for Command to be
 * automatically retried when a non-transient exception occurs. The Command Dispatch Interceptors can intercept and
 * alter command dispatched on this specific gateway. Typically, this would be used to add gateway specific meta data
 * to the Command.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultCommandGateway implements CommandGateway {

    private final CommandBus commandBus;
    private final RetryScheduler retryScheduler;
    private final List<CommandDispatchInterceptor> dispatchInterceptors;

    /**
     * Initializes a command gateway that dispatches commands to the given <code>commandBus</code> after they have been
     * handles by the given <code>commandDispatchInterceptors</code>. Commands will not be retried when command
     * execution fails.
     *
     * @param commandBus                  The CommandBus on which to dispatch the Command Messages
     * @param commandDispatchInterceptors The interceptors to invoke before dispatching commands to the Command Bus
     */
    public DefaultCommandGateway(CommandBus commandBus, CommandDispatchInterceptor... commandDispatchInterceptors) {
        this(commandBus, null, commandDispatchInterceptors);
    }

    /**
     * Initializes a command gateway that dispatches commands to the given <code>commandBus</code> after they have been
     * handles by the given <code>commandDispatchInterceptors</code>. When command execution results in an unchecked
     * exception, the given <code>retryScheduler</code> is invoked to allow it to retry that command.
     * execution fails.
     *
     * @param commandBus                  The CommandBus on which to dispatch the Command Messages
     * @param retryScheduler              The scheduler that will decide whether to reschedule commands
     * @param commandDispatchInterceptors The interceptors to invoke before dispatching commands to the Command Bus
     */
    public DefaultCommandGateway(CommandBus commandBus, RetryScheduler retryScheduler,
                                 CommandDispatchInterceptor... commandDispatchInterceptors) {
        this(commandBus, retryScheduler, asList(commandDispatchInterceptors));
    }

    /**
     * Initializes a command gateway that dispatches commands to the given <code>commandBus</code> after they have been
     * handles by the given <code>commandDispatchInterceptors</code>. When command execution results in an unchecked
     * exception, the given <code>retryScheduler</code> is invoked to allow it to retry that command.
     * execution fails.
     *
     * @param commandBus                  The CommandBus on which to dispatch the Command Messages
     * @param retryScheduler              The scheduler that will decide whether to reschedule commands
     * @param commandDispatchInterceptors The interceptors to invoke before dispatching commands to the Command Bus
     */
    public DefaultCommandGateway(CommandBus commandBus, RetryScheduler retryScheduler,
                                 List<CommandDispatchInterceptor> commandDispatchInterceptors) {
        this.retryScheduler = retryScheduler;
        this.commandBus = commandBus;
        this.dispatchInterceptors = new ArrayList<CommandDispatchInterceptor>(commandDispatchInterceptors);
    }

    public <R> void send(Object command, CommandCallback<R> callback) {
        CommandMessage commandMessage = processInterceptors(asCommandMessage(command));
        CommandCallback<R> commandCallback = callback;
        if (retryScheduler != null) {
            commandCallback = new RetryingCallback<R>(callback, commandMessage, retryScheduler, commandBus);
        }
        commandBus.dispatch(commandMessage, commandCallback);
    }

    private CommandMessage processInterceptors(CommandMessage commandMessage) {
        CommandMessage message = commandMessage;
        for (CommandDispatchInterceptor dispatchInterceptor : dispatchInterceptors) {
            message = dispatchInterceptor.handle(message);
        }
        return message;
    }

    /**
     * Sends the given <code>command</code> and waits for its execution to complete, or until the waiting thread is
     * interrupted.
     *
     * @param command The command to send
     * @param <R>     The expected type of return value
     * @return The result of the command handler execution
     *
     * @throws org.axonframework.commandhandling.CommandExecutionException
     *          when command execution threw a checked exception
     */
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command) {
        return (R) doSend(command).getResult();
    }

    /**
     * Sends the given <code>command</code> and waits for its execution to complete, or until the given
     * <code>timeout</code> has expired, or the waiting thread is interrupted.
     * <p/>
     * When the timeout occurs, or the thread is interrupted, this method returns <code>null</code>.
     *
     * @param command The command to send
     * @param timeout The maximum time to wait
     * @param unit    The time unit of the timeout argument
     * @param <R>     The expected type of return value
     * @return The result of the command handler execution
     *
     * @throws org.axonframework.commandhandling.CommandExecutionException
     *          when command execution threw a checked exception
     */
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command, long timeout, TimeUnit unit) {
        return (R) doSend(command).getResult(timeout, unit);
    }

    /**
     * Sends the given <code>command</code> and returns immediately. This implementation
     *
     * @param command The command to send
     */
    public void send(Object command) {
        send(command, new NoOpCallback());
    }

    private <R> FutureCallback<R> doSend(Object command) {
        FutureCallback<R> futureCallback = new FutureCallback<R>();
        send(command, futureCallback);
        return futureCallback;
    }
}
