package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.RingBuffer;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;

/**
 * Wrapper for command handler Callbacks that detects blacklisted aggregates and starts a cleanup process when an
 * aggregate is blacklisted.
 *
 * @author Allard Buijze
 * @since 2.0
 */
class BlacklistDetectingCallback<T extends EventSourcedAggregateRoot, R>
        implements CommandCallback<R> {

    private final CommandCallback<R> delegate;
    private final CommandMessage command;
    private final RingBuffer<CommandHandlingEntry<T>> ringBuffer;
    private final DisruptorCommandBus<T> commandBus;
    private boolean rescheduleOnCorruptState;

    public BlacklistDetectingCallback(CommandCallback<R> delegate, CommandMessage command,
                                      RingBuffer<CommandHandlingEntry<T>> ringBuffer,
                                      DisruptorCommandBus<T> commandBus, boolean rescheduleOnCorruptState) {
        this.delegate = delegate;
        this.command = command;
        this.ringBuffer = ringBuffer;
        this.commandBus = commandBus;
        this.rescheduleOnCorruptState = rescheduleOnCorruptState;
    }

    @Override
    public void onSuccess(R result) {
        delegate.onSuccess(result);
    }

    @Override
    public void onFailure(Throwable cause) {
        if (cause instanceof AggregateBlacklistedException) {
            long sequence = ringBuffer.next();
            CommandHandlingEntry event = ringBuffer.get(sequence);
            event.resetAsRecoverEntry(((AggregateBlacklistedException) cause).getAggregateIdentifier());
            ringBuffer.publish(sequence);
            delegate.onFailure(cause.getCause());
        } else if (rescheduleOnCorruptState && cause instanceof AggregateStateCorruptedException) {
            commandBus.doDispatch(command, delegate);
        } else {
            delegate.onFailure(cause);
        }
    }

    public boolean hasDelegate() {
        return delegate != null;
    }
}
