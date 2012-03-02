package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.RingBuffer;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;

/**
 * @author Allard Buijze
 */
class BlacklistDetectingCallback<T extends EventSourcedAggregateRoot, R>
        implements CommandCallback<R> {

    private final CommandCallback<R> delegate;
    private final RingBuffer<CommandHandlingEntry<T>> ringBuffer;

    public BlacklistDetectingCallback(CommandCallback<R> delegate,
                                      RingBuffer<CommandHandlingEntry<T>> ringBuffer) {
        this.delegate = delegate;
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void onSuccess(R result) {
        if (delegate != null) {
            delegate.onSuccess(result);
        }
    }

    @Override
    public void onFailure(Throwable cause) {
        if (cause instanceof AggregateBlacklistedException) {
            long sequence = ringBuffer.next();
            CommandHandlingEntry event = ringBuffer.get(sequence);
            event.resetAsRecoverEntry(((AggregateBlacklistedException) cause).getAggregateIdentifier());
            ringBuffer.publish(sequence);
        }
        if (delegate != null) {
            delegate.onFailure(cause);
        }
    }

    public boolean hasDelegate() {
        return delegate != null;
    }
}
