/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a {@link ListenerErrorHandler} that logs exceptions as errors but otherwise does nothing to
 * prevent event handling from continuing.
 *
 * @author Rene de Waele
 */
public class LoggingListenerErrorHandler implements ListenerErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(LoggingListenerErrorHandler.class);

    @Override
    public void onError(Exception exception, EventMessage<?> event, EventListener eventListener) {
        logger.error(String.format("EventListener [%s] failed to handle an event of type [%s]. " +
                                           "Continuing processing with next listener", eventListener,
                                   event.getPayloadType().getName()), exception);
    }
}
