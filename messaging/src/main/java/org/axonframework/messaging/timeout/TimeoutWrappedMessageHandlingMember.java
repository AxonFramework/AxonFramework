package org.axonframework.messaging.timeout;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import java.util.concurrent.Callable;
import javax.annotation.Nonnull;

import static org.axonframework.messaging.timeout.AxonTaskUtils.runTaskWithTimeout;

/**
 * Represents a {@link MessageHandlingMember} that wraps the original handler in a timeout. It does this by running a
 * task via {@link AxonTaskUtils#runTaskWithTimeout(String, Callable, int, int, int)}.
 *
 * @param <T> The type of the target object
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
        return runTaskWithTimeout(
                String.format("Message [%s] for handler [%s]",
                              message.getPayloadType().getName(),
                              target != null ? target.getClass().getName() : null),
                () -> super.handle(message, target),
                timeout,
                warningThreshold,
                warningInterval
        );
    }
}
