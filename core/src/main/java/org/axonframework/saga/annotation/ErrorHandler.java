package org.axonframework.saga.annotation;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.async.RetryPolicy;
import org.axonframework.saga.Saga;

/**
 * Defines the behavior of a component when an error occurs during Event Processing in a Saga.
 *
 * @author Allard Buijze
 * @since 2.4.2
 */
public interface ErrorHandler {

    /**
     * Invoked when an error occurs preparing Sagas. This is the phase where sagas are looked up using their
     * associations.
     *
     * @param sagaType        The type of Saga to prepare
     * @param publishedEvent  The event being published
     * @param invocationCount The number of attempts to prepare (is always at least 1)
     * @param exception       The exception that occurred in this attempt
     * @return the expected behavior for the event handling component
     */
    RetryPolicy onErrorPreparing(Class<? extends Saga> sagaType, EventMessage<?> publishedEvent,
                                 int invocationCount, Exception exception);

    /**
     * Invoked when an error occurs when a Saga instance is invoked. These are errors thrown by the Saga itself.
     *
     * @param saga            The Saga instance being invoked
     * @param publishedEvent  The event handled by the Saga
     * @param invocationCount The number of times this event has been offered to the Saga, including the last, failed,
     *                        attempt
     * @param exception       The exception that occurred in the last attempt to invoke the Saga
     * @return The Policy describing what the SagaManager should do with this exception (retry, skip, etc)
     */
    RetryPolicy onErrorInvoking(Saga saga, EventMessage publishedEvent, int invocationCount, Exception exception);
}
