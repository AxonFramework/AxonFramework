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

/**
 * Interface of an error handler that is invoked when an exception is triggered as result of an {@link Saga} handling an
 * event.
 *
 * @author Milan Savic
 * @since 3.2
 */
public interface SagaInvocationErrorHandler {

    /**
     * Invoked after given {@code saga} failed to handle given {@code event}. Implementations have a choice of options
     * for how to continue:
     * <p>
     * <ul> <li>To ignore this error no special action is required. Processing will continue for this and subsequent
     * events.</li> <li>To retry processing the event, implementations can re-invoke {@link Saga#handle(EventMessage)}
     * on the {@code saga} once or multiple times.</li> <li>To terminate event handling altogether and stop propagating
     * the event to other listeners implementations may throw an exception.</li></ul>
     *
     * @param exception The exception thrown by the given {@code saga}
     * @param event     The {@code event} that triggered the exception
     * @param saga      The {@code saga} that failed to handle given event
     * @throws Exception To stop further handling of the event
     */
    void onError(Exception exception, EventMessage event, Saga saga) throws Exception;
}
