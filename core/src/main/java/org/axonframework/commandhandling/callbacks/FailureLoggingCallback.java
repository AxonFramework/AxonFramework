package org.axonframework.commandhandling.callbacks;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CommandCallback} implementation wrapping another, that concisely logs failed commands. Since the full
 * exception is being reported to the delegate callback, the full stacktrace is not logged.
 *
 * @param <C> The type of payload of the command
 * @param <R> The return value of the command handler
 */
public class FailureLoggingCallback<C, R> implements CommandCallback<C, R> {

    private final CommandCallback<C, R> delegate;
    private final Logger logger;

    /**
     * Initialize the callback to delegate calls to the given {@code delegate}, logging failures on a logger
     * for this class (on warn level).
     *
     * @param delegate The command callback to forward invocations to
     */
    public FailureLoggingCallback(CommandCallback<C, R> delegate) {
        this(LoggerFactory.getLogger(FailureLoggingCallback.class), delegate);
    }

    /**
     * Initialize the callback to delegate calls to the given {@code delegate}, logging failures on the given
     * {@code logger} (on warn level).
     *
     * @param logger   The logger to log exceptions on
     * @param delegate The command callback to forward invocations to
     */
    public FailureLoggingCallback(Logger logger, CommandCallback<C, R> delegate) {
        this.logger = logger;
        this.delegate = delegate;
    }

    @Override
    public void onSuccess(CommandMessage<? extends C> commandMessage, R result) {
        delegate.onSuccess(commandMessage, result);
    }

    @Override
    public void onFailure(CommandMessage<? extends C> commandMessage, Throwable cause) {
        logger.warn("Command '{}' resulted in {}({})",
                    commandMessage.getCommandName(), cause.getClass().getName(), cause.getMessage());
        delegate.onFailure(commandMessage, cause);
    }
}
