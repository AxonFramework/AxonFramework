package org.axonframework.queryhandling;

import org.axonframework.messaging.MessageHandler;

/**
 * Interface describing a mechanism for the QueryMessage components to report errors.
 * <p>
 * Note that this handler is not used for point-to-point queries. In that case, exceptions are reported directly
 * to the invoker. Query Bus implementation may choose to implement point-to-point queries as scatter-gather, and
 * only reporting the first answer returned. In that case, this handler is invoked, and any propagated exceptions
 * may or may not be reported to the sender of the query.
 *
 * @author Allard Buijze
 * @author Marc Gathier
 * @since 3.1
 */
public interface QueryInvocationErrorHandler {

    /**
     * Invoked when an error occurred while invoking a message handler in a scatter-gather query. Implementations may
     * throw an exception to propagate the error to the caller. In that case, the QueryBus implementation will receive
     * the exception, and may choose to propagate the error to the sender of the query.
     * <p>
     * Note that this handler is not used for point-to-point queries. In that case, exceptions are reported directly
     * to the invoker. Query Bus implementation may choose to implement point-to-point queries as scatter-gather, and
     * only reporting the first answer returned. In that case, this method is invoked, and any propagated exceptions
     * may or may not be reported to the sender of the query.
     *
     * @param error          The error that occurred
     * @param queryMessage   The message causing the exception in the handler
     * @param messageHandler The handler that reported the exception
     */
    void onError(Throwable error, QueryMessage<?, ?> queryMessage, MessageHandler messageHandler);
}
