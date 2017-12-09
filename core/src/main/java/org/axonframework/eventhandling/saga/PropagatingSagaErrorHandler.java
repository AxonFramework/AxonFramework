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
 *
 */

package org.axonframework.eventhandling.saga;

import org.axonframework.eventhandling.ErrorContext;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventMessage;

/**
 * Singleton which implements {@link ErrorHandler} and {@link SagaInvocationErrorHandler} by propagating exceptions
 * further.
 *
 * @author Milan Savic
 * @since 3.2
 */
public enum PropagatingSagaErrorHandler implements ErrorHandler, SagaInvocationErrorHandler {

    /**
     * Singleton instance of {@link PropagatingSagaErrorHandler}.
     */
    INSTANCE;

    @Override
    public void handleError(ErrorContext errorContext) throws Exception {
        throw errorContext.error();
    }

    @Override
    public void onError(Exception exception, EventMessage event, Saga saga) throws Exception {
        throw exception;
    }
}
