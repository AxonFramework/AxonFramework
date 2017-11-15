package org.axonframework.queryhandling;

import org.axonframework.messaging.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the QueryInvocationErrorHandler that logs all errors to a given {@link Logger}.
 */
public class LoggingQueryInvocationErrorHandler implements QueryInvocationErrorHandler {

    private final Logger logger;

    /**
     * Initialize the error handler to log to a logger registered for this class.
     */
    public LoggingQueryInvocationErrorHandler() {
        this(LoggerFactory.getLogger(LoggingQueryInvocationErrorHandler.class));
    }

    /**
     * Initialize the error handler to use the given {@code logger} to log errors from query handlers.
     *
     * @param logger The logger to log errors with
     */
    public LoggingQueryInvocationErrorHandler(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void onError(Throwable error, QueryMessage<?, ?> queryMessage, MessageHandler messageHandler) {
        logger.warn("An error occurred while processing query message [{}]", queryMessage.getQueryName(), error);
    }
}
