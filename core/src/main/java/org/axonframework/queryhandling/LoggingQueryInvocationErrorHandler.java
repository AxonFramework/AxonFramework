/*
 * Copyright (c) 2010-2017. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
