package org.axonframework.saga.annotation;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.async.RetryPolicy;
import org.axonframework.saga.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ErrorHandler implementation that logs the error and proceeds with the next event.
 *
 * @author Allard Buijze
 * @since 2.4.2
 */
public class ProceedingErrorHandler implements ErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProceedingErrorHandler.class);

    @Override
    public RetryPolicy onErrorPreparing(Class<? extends Saga> sagaType, EventMessage<?> publishedEvent,
                                        int invocationCount, Exception e) {
        logger.error("An error occurred while trying to prepare sagas of type [{}] for invocation of event [{}]. "
                             + "Proceeding with the next event.",
                     sagaType.getName(), publishedEvent.getPayloadType().getName(), e);
        return RetryPolicy.proceed();
    }

    @Override
    public RetryPolicy onErrorInvoking(Saga saga, EventMessage publishedEvent, int invocationCount, Exception e) {
        logger.error("Saga threw an exception while handling an Event. Ignoring and moving on...", e);
        return RetryPolicy.proceed();
    }
}
