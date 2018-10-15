/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

/**
 * Singleton ErrorHandler implementation that does not do anything.
 *
 * @author Rene de Waele
 */
public enum PropagatingErrorHandler implements ErrorHandler, ListenerInvocationErrorHandler {

    /**
     * Singleton instance of a {@link PropagatingErrorHandler}.
     */
    INSTANCE;

    /**
     * Singleton instance of a {@link PropagatingErrorHandler}.
     * @return the singleton instance of {@link PropagatingErrorHandler}
     */
    public static PropagatingErrorHandler instance() {
        return INSTANCE;
    }

    @Override
    public void onError(Exception exception, EventMessage<?> event, EventMessageHandler eventHandler) throws Exception {
        throw exception;
    }

    @Override
    public void handleError(ErrorContext errorContext) throws Exception {
        throw (Exception) errorContext.error();
    }
}
