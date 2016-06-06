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

/**
 * @author Rene de Waele
 */
public interface ErrorHandler {

    /**
     * Invoked after given <code>eventListener</code> failed to handle given <code>event</code>. Implementations have a
     * choice of options for how to continue:
     * <p>
     * <ul> <li>To ignore this error no special action is required. Processing will continue for this and subsequent
     * events.</li> <li>To retry processing the event, implementations can re-invoke {@link
     * EventListener#handle(EventMessage)} on the eventListener once or multiple times.</li> <li>To retry processing at
     * a later time schedule a task to re-invoke the eventListener.</li> <li>To terminate event processing altogether
     * implementations may throw an exception. Given default configuration this will roll back the Unit of Work involved
     * in the processing of this event and events that are processed in the same batch.</li></ul>
     *
     * @param exception
     * @param event
     * @param eventListener
     * @param eventProcessor
     * @throws Exception
     */
    void onError(Exception exception, EventMessage<?> event, EventListener eventListener, String eventProcessor) throws Exception;

}
