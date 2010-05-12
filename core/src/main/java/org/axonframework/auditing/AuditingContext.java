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

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Context object that stores information related to the current command for auditing purposes. The context contains the
 * command that was handled, the events that were generated as part of the command handling as well as a correlation ID
 * and the principal that the command was handled for.
 * <p/>
 * Note that the principal can be <code>null</code> if no security provider is present.
 *
 * @author Allard Buijze
 * @see org.axonframework.auditing.AuditedDomainEvent
 * @since 0.6
 */
public class AuditingContext {

    private final List<Event> events = new LinkedList<Event>();
    private final Object command;
    private final Principal principal;
    private final UUID correlationId;

    /**
     * Creates an auditing context with given <code>principal</code> and <code>command</code>, using a randomly
     * generated correlation ID.
     *
     * @param principal The principal to associate with the current auditing context
     * @param command   The command that was sent
     */
    AuditingContext(Principal principal, Object command) {
        this(principal, UUID.randomUUID(), command);
    }

    AuditingContext(Principal principal, UUID correlationId, Object command) {
        this.principal = principal;
        this.correlationId = correlationId;
        this.command = command;
    }

    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void registerEvent(Event event) {
        events.add(event);
    }

    public Principal getPrincipal() {
        return principal;
    }

    public Object getCommand() {
        return command;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }
}
