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

package org.axonframework.test.saga;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;

import java.util.Optional;

public class RecordingLoggingErrorHandler extends LoggingErrorHandler {

    private Optional<Exception> exception = Optional.empty();

    @Override
    public void onError(Exception exception, EventMessage<?> event, EventMessageHandler eventHandler) {
        this.exception = Optional.of(exception);
        super.onError(exception, event, eventHandler);
    }

    public Optional<Exception> getException() {
        return exception;
    }
}
