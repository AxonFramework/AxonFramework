package org.axonframework.commandhandling.disruptor;

/**
 * Exception indicating that an aggregate has been blacklisted by the DisruptorCommandBus. The cause of the
 * blacklisting is provided in the {@link #getCause()} of the exception.
 * <p/>
 * Aggregates are blacklisted when the DisruptorCommandBus cannot guarantee that the state of the aggregate in its
 * cache is correct. A cleanup notification will be handled by the disruptor to recover from this, by clearing the
 * cached data of the aggregate. After the cached information has been cleared, the blacklist status is removed.
 * <p/>
 * It is generally safe to retry any commands that resulted in this exception, unless the cause is clearly
 * non-transient.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AggregateBlacklistedException extends AggregateStateCorruptedException {

    private static final long serialVersionUID = -6223847897300183682L;

    /**
     * Initializes the exception with given <code>aggregateIdentifier</code>, given explanatory <code>message</code>
     * and <code>cause</code>.
     *
     * @param aggregateIdentifier The identifier of the blacklisted aggregate
     * @param message             The message explaining why the blacklisting occurred
     * @param cause               The cause of the blacklist
     */
    public AggregateBlacklistedException(Object aggregateIdentifier, String message, Throwable cause) {
        super(aggregateIdentifier, message, cause);
    }
}
