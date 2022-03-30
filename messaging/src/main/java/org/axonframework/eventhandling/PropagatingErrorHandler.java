/*
 * Copyright (c) 2010-2022. Axon Framework
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

import javax.annotation.Nonnull;

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
     *
     * @return the singleton instance of {@link PropagatingErrorHandler}
     */
    public static PropagatingErrorHandler instance() {
        return INSTANCE;
    }

    @Override
    public void onError(@Nonnull Exception exception, @Nonnull EventMessage<?> event,
                        @Nonnull EventMessageHandler eventHandler) throws Exception {
        throw exception;
    }

    @Override
    public void handleError(@Nonnull ErrorContext errorContext) throws Exception {
        Throwable error = errorContext.error();
        if (error instanceof Error) {
            throw (Error) error;
        } else if (error instanceof Exception) {
            throw (Exception) error;
        } else {
            throw new EventProcessingException("An error occurred while handling an event", error);
        }
    }
}
