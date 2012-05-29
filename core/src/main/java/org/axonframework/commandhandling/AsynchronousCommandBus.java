package org.axonframework.commandhandling;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Specialization of the SimpleCommandBus that processed Commands asynchronously from the calling thread. By default,
 * the AsynchronousCommandBus uses a Cached Thread Pool (see
 * {@link java.util.concurrent.Executors#newCachedThreadPool()}). It will reuse threads while possible, and shut them
 * down after 60 seconds of inactivity.
 * <p/>
 * Each Command is dispatched in a separate task, which is processed by the Executor.
 * <p/>
 * Note that you should call {@link #shutdown()} to stop any threads waiting for new tasks. Failure to do so may cause
 * the JVM to hang for up to 60 seconds on JVM shutdown.
 *
 * @author Allard Buijze
 * @since 1.3.4
 */
public class AsynchronousCommandBus extends SimpleCommandBus {

    private final Executor executor;

    /**
     * Initialize the AsynchronousCommandBus, using a Cached Thread Pool.
     */
    public AsynchronousCommandBus() {
        this(Executors.newCachedThreadPool());
    }

    /**
     * Initialize the AsynchronousCommandBus using the given <code>executor</code>.
     *
     * @param executor The executor that processes Command dispatching threads
     */
    public AsynchronousCommandBus(Executor executor) {
        this.executor = executor;
    }

    /**
     * Initialize the AsynchronousCommandBus using the given <code>executor</code> and allowing you to optionally
     * register MBeans containing statistics.
     *
     * @param executor       The executor that processes Command dispatching threads
     * @param registerMBeans true to register the mbeans, false for not registering them.
     */
    public AsynchronousCommandBus(Executor executor, boolean registerMBeans) {
        super(registerMBeans);
        this.executor = executor;
    }

    @Override
    public void dispatch(Object command) {
        executor.execute(new DispatchCommand<Object>(command, null));
    }

    @Override
    public <R> void dispatch(Object command, CommandCallback<R> callback) {
        executor.execute(new DispatchCommand<R>(command, callback));
    }

    /**
     * Shuts down the Executor used to asynchronously dispatch incoming commands. If the <code>Executor</code> provided
     * in the constructor does not implement <code>ExecutorService</code>, this method does nothing.
     */
    public void shutdown() {
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
            try {
                ((ExecutorService) executor).awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // we've been interrupted. Reset the interruption flag and continue
                Thread.currentThread().interrupt();
            }
        }
    }

    private class DispatchCommand<R> implements Runnable {

        private final Object command;
        private final CommandCallback<R> callback;

        public DispatchCommand(Object command, CommandCallback<R> callback) {
            this.command = command;
            this.callback = callback;
        }

        @Override
        public void run() {
            if (callback == null) {
                AsynchronousCommandBus.super.dispatch(command);
            } else {
                AsynchronousCommandBus.super.dispatch(command, callback);
            }
        }
    }
}
