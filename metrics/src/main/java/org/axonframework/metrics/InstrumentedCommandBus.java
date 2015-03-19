package org.axonframework.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Wrapper for a CommandBus implementation that reports usage statistics of the Command Bus. It exposes the following
 * metrics:
 * <ul>
 * <li><code>supported-commands</code>: A Gauge containing the list of command names supported by this Command Bus. It
 * only
 * reports commands for which a handler has been subscribed to this instance of the bus. When handlers are subscribed
 * directly with the wrapped Command Bus, they are not reported. See {@link #getSupportedCommands()}.</li>
 * <li><code>handling</code>: A Counter indicating the number of commands currently being handled by the command bus.
 * See {@link #getHandlingCounter()}.</li>
 * <li><code>success</code>: A Counter indicating the number of successfully executed commands. See
 * {@link #getSuccessCounter()}.</li>
 * <li><code>failure</code>: A Counter indicating the number of failed commands. See {@link #getFailureCounter()}.</li>
 * <li><code>command-response-time</code>: A Timer that times the execution time of a command. That is the time between
 * it was dispatched and the time where the callback (if provided) was invoked. See {@link #getCommandTimer()}.</li>
 * </ul>
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class InstrumentedCommandBus implements CommandBus, MetricSupport {

    private final MetricRegistry metricRegistry = new MetricRegistry();
    private final Set<String> supportedCommands = new CopyOnWriteArraySet<>();
    private final CommandBus delegate;
    private final Counter handlingCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer commandTimer;
    private final Gauge<Set<String>> supportedCommandsGauge;

    /**
     * Initialize the InstrumentedCommandBus, measuring behavior of the given <code>delegate</code>
     *
     * @param delegate The CommandBus instance to measure
     */
    public InstrumentedCommandBus(CommandBus delegate) {
        this.delegate = delegate;
        supportedCommandsGauge = () -> new TreeSet<>(supportedCommands);
        metricRegistry.register("supported-commands", supportedCommandsGauge);
        handlingCounter = metricRegistry.counter("handling");
        successCounter = metricRegistry.counter("success");
        failureCounter = metricRegistry.counter("failure");
        commandTimer = metricRegistry.timer("command-response-time");
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        final Timer.Context time = commandTimer.time();
        handlingCounter.inc();
        try {
            delegate.dispatch(command, new TimingCommandCallback<>(time, callback));
        } catch (Exception e) {
            handlingCounter.dec();
            failureCounter.inc();
            time.close();
            throw e;
        }
    }

    @Override
    public <C> void subscribe(String commandName, CommandHandler<? super C> handler) {
        delegate.subscribe(commandName, handler);
        supportedCommands.add(commandName);
    }

    @Override
    public <C> boolean unsubscribe(String commandName, CommandHandler<? super C> handler) {
        supportedCommands.remove(commandName);
        return delegate.unsubscribe(commandName, handler);
    }

    @Override
    public MetricSet getMetricSet() {
        return metricRegistry;
    }

    /**
     * Returns the counter that keeps track of the number of commands currently being handled. This is the approximate
     * number of commands that have been dispatched, but haven't had their callback (when provided) invoked yet.
     *
     * @return the counter that tracks the number of commands being handled.
     */
    public Counter getHandlingCounter() {
        return handlingCounter;
    }

    /**
     * The counter tracking the number of commands that executed successfully. A successful command is a command for
     * which the callback's {@link org.axonframework.commandhandling.CommandCallback#onSuccess(org.axonframework.commandhandling.CommandMessage,
     * Object)} method was
     * invoked (or would have been invoked, when no callback was provided).
     *
     * @return the counter that tracks the number of successfully executed commands
     */
    public Counter getSuccessCounter() {
        return successCounter;
    }

    /**
     * The counter tracking the number of failed commands. A command counts as failed when the callback's
     * {@link org.axonframework.commandhandling.CommandCallback#onFailure(org.axonframework.commandhandling.CommandMessage,
     * Throwable)} method was invoked (or would
     * have been invoked, when no callback was provided).
     *
     * @return the counter that tracks the number of commands that resulted in a failure.
     */
    public Counter getFailureCounter() {
        return failureCounter;
    }

    /**
     * Returns the Timer that tracks the processing times for commands. This is the time between the invocation of the
     * dispatch method and the time the callback was invoked.
     *
     * @return the Timer that tracks processing times for commands.
     */
    public Timer getCommandTimer() {
        return commandTimer;
    }

    /**
     * Returns the Gauge that reports the names of the supported commands for the instrumented command bus. The value
     * reported by the Gauge is a view of the supported commands at the time the Gauge was polled.
     *
     * @return the Gauge that reports the supported commands
     */
    public Gauge<Set<String>> getSupportedCommands() {
        return supportedCommandsGauge;
    }

    private class TimingCommandCallback<C, R> implements CommandCallback<C, R> {

        private final Timer.Context timerContext;
        private final CommandCallback<C, R> delegate;

        public TimingCommandCallback(Timer.Context timerContext, CommandCallback<C, R> delegate) {
            this.timerContext = timerContext;
            this.delegate = delegate;
        }

        @Override
        public void onSuccess(CommandMessage<? extends C> commandMessage, R result) {
            try {
                delegate.onSuccess(commandMessage, result);
            } finally {
                successCounter.inc();
                updateMetrics();
            }
        }

        @Override
        public void onFailure(CommandMessage<? extends C> commandMessage, Throwable cause) {
            try {
                delegate.onFailure(commandMessage, cause);
            } finally {
                failureCounter.inc();
                updateMetrics();
            }
        }

        private void updateMetrics() {
            handlingCounter.dec();
            timerContext.close();
        }
    }
}
