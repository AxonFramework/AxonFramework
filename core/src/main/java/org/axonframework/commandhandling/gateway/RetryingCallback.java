package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Callback implementation that will invoke a retry scheduler if a command results in a runtime exception.
 * <p/>
 * Generally, it is not necessary to use this class directly. It is used by CommandGateway implementations to support
 * retrying of commands.
 *
 * @author Allard Buijze
 * @see DefaultCommandGateway
 * @since 2.0
 */
public class RetryingCallback<R> implements CommandCallback<R> {

    private final CommandCallback<R> delegate;
    private final CommandMessage commandMessage;
    private final RetryScheduler retryScheduler;
    private final CommandBus commandBus;
    private final List<Class<? extends Throwable>[]> history;
    private final Runnable dispatchCommand;

    /**
     * Initialize the RetryingCallback with the given <code>delegate</code>, representing the actual callback passed as
     * a parameter to dispatch, the given <code>commandMessage</code>, <code>retryScheduler</code> and
     * <code>commandBus</code>.
     *
     * @param delegate       The callback to invoke when the command succeeds, or when retries are rejected.
     * @param commandMessage The message being dispatched
     * @param retryScheduler The scheduler that decides if and when a retry should be scheduled
     * @param commandBus     The commandBus on which the command must be dispatched
     */
    public RetryingCallback(CommandCallback<R> delegate, CommandMessage commandMessage, RetryScheduler retryScheduler,
                            CommandBus commandBus) {
        this.delegate = delegate;
        this.commandMessage = commandMessage;
        this.retryScheduler = retryScheduler;
        this.commandBus = commandBus;
        this.history = new ArrayList<Class<? extends Throwable>[]>();
        this.dispatchCommand = new RetryDispatch();
    }

    @Override
    public void onSuccess(R result) {
        delegate.onSuccess(result);
    }

    @Override
    public void onFailure(Throwable cause) {
        history.add(simplify(cause));
        try {
        if (!(cause instanceof RuntimeException)
                || !retryScheduler.scheduleRetry(commandMessage, (RuntimeException) cause,
                                                 new ArrayList<Class<? extends Throwable>[]>(history),
                                                 dispatchCommand)) {
            delegate.onFailure(cause);
        }
        } catch (Exception e) {
            delegate.onFailure(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Throwable>[] simplify(Throwable cause) {
        List<Class<? extends Throwable>> types = new ArrayList<Class<? extends Throwable>>();
        types.add(cause.getClass());
        Throwable rootCause = cause;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
            types.add(rootCause.getClass());
        }
        return types.toArray(new Class[types.size()]);
    }

    private class RetryDispatch implements Runnable {

        @Override
        public void run() {
            commandBus.dispatch(commandMessage, RetryingCallback.this);
        }
    }
}
