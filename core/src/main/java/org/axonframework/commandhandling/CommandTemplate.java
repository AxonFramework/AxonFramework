package org.axonframework.commandhandling;

import org.axonframework.commandhandling.callbacks.FutureCallback;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class simplifies the use of the CommandBus, by providing methods for common usage patterns. Once constructed,
 * this class is safe for use in a multi-threaded environment.
 * <p/>
 * The <code>sendAndWait</code> methods in this template throw any runtime exceptions and errors that resulted from
 * command execution as-is. Checked exceptions are wrapped in a {@link CommandExecutionException}.
 *
 * @author Allard Buijze
 * @since 1.3
 */
public class CommandTemplate {

    private final CommandBus commandBus;

    /**
     * Constructs a CommandTemplate that uses the given <code>commandBus</code> to send its commands.
     *
     * @param commandBus the CommandBus on which commands must be sent
     */
    public CommandTemplate(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Sends the given <code>command</code> and waits for its execution to complete, or until the waiting thread is
     * interrupted.
     *
     * @param command The command to send
     * @param <R>     The expected type of return value
     * @return The result of the command handler execution
     *
     * @throws InterruptedException when the thread is interrupted while waiting
     */
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command) throws InterruptedException {
        return (R) doSend(command).getResult();
    }

    /**
     * Sends the given <code>command</code> and waits for its execution to complete, until the given
     * <code>timeout</code> has expired, or the waiting thread is interrupted.
     *
     * @param command The command to send
     * @param timeout The maximum time to wait
     * @param unit    The time unit of the timeout argument
     * @param <R>     The expected type of return value
     * @return The result of the command handler execution
     *
     * @throws InterruptedException when the thread is interrupted while waiting
     * @throws TimeoutException     when the given timeout has expired while waiting for a result
     */
    @SuppressWarnings("unchecked")
    public <R> R sendAndWait(Object command, long timeout, TimeUnit unit) throws TimeoutException,
                                                                                 InterruptedException {
        return (R) doSend(command).getResult(timeout, unit);
    }

    /**
     * Sends the given <code>command</code> and returns a Future instance that allows the caller to retrieve the result
     * at an appropriate time.
     *
     * @param command The command to send
     * @param <R>     The type of return value expected
     * @return a future providing access to the execution result
     */
    public <R> Future<R> send(Object command) {
        return doSend(command);
    }

    private <R> FutureCallback<R> doSend(Object command) {
        FutureCallback<R> futureCallback = new FutureCallback<R>();
        commandBus.dispatch(command, futureCallback);
        return futureCallback;
    }
}
