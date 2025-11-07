/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.errorhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.processing.EventProcessingException;

/**
 * An {@link ErrorHandler} implementation that rethrows the {@link ErrorContext#error() ErrorContext exception}.
 *
 * @author Rene de Waele
 * @since 3.0.0
 */
public enum PropagatingErrorHandler implements ErrorHandler {

    /**
     * Singleton instance of a {@link PropagatingErrorHandler}.
     */
    INSTANCE;

    /**
     * Singleton instance of a {@code PropagatingErrorHandler}.
     *
     * @return The singleton instance of {@code PropagatingErrorHandler}
     */
    public static PropagatingErrorHandler instance() {
        return INSTANCE;
    }

    @Override
    public void handleError(@Nonnull ErrorContext errorContext) throws Exception {
        Throwable error = errorContext.error();
        if (error instanceof Error) {
            throw (Error) error;
        } else if (error instanceof Exception) {
            throw (Exception) error;
        } else {
            throw new EventProcessingException("An error occurred while handling an event.", error);
        }
    }
}
