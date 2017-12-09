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
 *
 */

package org.axonframework.eventhandling.saga;

import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

/**
 * Implementation of {@link SagaInvocationErrorHandler} that logs exceptions as errors but otherwise does nothing to
 * prevent event handling from continuing.
 *
 * @author Milan Savic
 * @since 3.2
 */
public class LoggingSagaErrorHandler implements SagaInvocationErrorHandler {

    private final Logger logger;

    /**
     * Initialize the {@link LoggingSagaErrorHandler} using the logger for this class.
     */
    public LoggingSagaErrorHandler() {
        this(LoggerFactory.getLogger(LoggingSagaErrorHandler.class));
    }

    /**
     * Initialize the {@link LoggingSagaErrorHandler} to use the given {@code logger} to log errors.
     *
     * @param logger the logger to log errors with
     */
    public LoggingSagaErrorHandler(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void onError(Exception exception, EventMessage event, Saga saga) {
        logger.error(format("An exception occurred while a Saga [%s] was handling an Event [%s]:",
                            saga.getClass().getSimpleName(), event.getPayloadType().getSimpleName()), exception);
    }
}
