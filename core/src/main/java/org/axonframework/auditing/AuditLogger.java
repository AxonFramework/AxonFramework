/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.auditing;

import org.axonframework.domain.Event;

import java.util.List;

/**
 * Interface describing a component capable of writing auditing entries to a log.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface AuditLogger {

    /**
     * Appends the given <code>command</code> and <code>events</code> to the logs.
     * <p/>
     * This method may be invoked in the thread dispatching the command. Therefore, considering writing asynchronously
     * when the underlying mechanism is slow.
     *
     * @param command The command that has been handled
     * @param events  The events that were generated during command handling
     */
    void append(Object command, List<Event> events);
}
