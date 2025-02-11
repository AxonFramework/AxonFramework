package org.axonframework.messaging.timeout;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

/**
 * Represents a {@link MessageHandlingMember} that wraps another {@link MessageHandlingMember} and enforces a timeout on
 * the invocation of the wrapped member. It does this by starting a {@link AxonTimeLimitedTask} and completes this upon
 * completion. When the execution takes too long, it will throw a {@link TimeoutException}.
 * <p>
 * If the {@code warningThreshold} is lower than the timeout, warnings will be logged at the configured
 * {@code warningInterval} before the timeout is reached.
 *
 * @param <T> The type of the target object
 * @author Mitchell Herrijgers
 * @since 4.11.0
 */
class TimeoutWrappedMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> {

    private final int timeout;
    private final int warningThreshold;
    private final int warningInterval;

    /**
     * Creates a new {@link TimeoutWrappedMessageHandlingMember} for the given {@code original} handler with the given
     * {@code timeout}, {@code warningThreshold} and {@code warningInterval}.
     *
     * @param original         The original handler to wrap
     * @param timeout          The timeout in milliseconds
     * @param warningThreshold The threshold in milliseconds after which a warning is logged. Setting this to a value
     *                         higher than {@code timeout} will disable warnings.
     * @param warningInterval  The interval in milliseconds between warnings.
     */
    TimeoutWrappedMessageHandlingMember(MessageHandlingMember<T> original,
                                        int timeout,
                                        int warningThreshold,
                                        int warningInterval) {
        super(original);
        this.timeout = timeout;
        this.warningThreshold = warningThreshold;
        this.warningInterval = warningInterval;
    }

    @Override
    public Object handle(@Nonnull Message<?> message, T target) throws Exception {
        String taskName = String.format("Message [%s] for handler [%s]",
                                        message.getPayloadType().getName(),
                                        target != null ? target.getClass().getName() : null);
        AxonTimeLimitedTask taskTimeout = new AxonTimeLimitedTask(
                taskName,
                timeout,
                warningThreshold,
                warningInterval
        );
        taskTimeout.start();
        try {
            return super.handle(message, target);
        } catch (InterruptedException e) {
            throw new TimeoutException(String.format("%s has timed out", taskName));
        } finally {
            taskTimeout.complete();
        }
    }

    /**
     * Returns the timeout of the message handler in milliseconds.
     * @return the timeout of the message handler in milliseconds
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Returns the threshold in milliseconds after which a warning is logged. Setting this to a value higher than
     * {@link #getTimeout()} will disable warnings.
     *
     * @return the threshold in milliseconds after which a warning is logged
     */
    public int getWarningThreshold() {
        return warningThreshold;
    }

    /**
     * Returns the interval in milliseconds between warnings.
     * @return the interval in milliseconds between warnings
     */
    public int getWarningInterval() {
        return warningInterval;
    }
}
