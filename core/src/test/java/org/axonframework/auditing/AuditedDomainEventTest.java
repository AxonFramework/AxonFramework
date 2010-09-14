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

import org.axonframework.domain.AggregateIdentifierFactory;
import org.junit.*;

import java.security.Principal;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AuditedDomainEventTest {

    @Test
    public void testCreateEvent_ContextFullyInitialized() {
        UUID correlationId = UUID.randomUUID();
        Principal stubPrincipal = new Principal() {
            @Override
            public String getName() {
                return "Principal";
            }
        };
        AuditingContextHolder.setContext(new AuditingContext(stubPrincipal, correlationId, "Command"));
        AuditedDomainEvent domainEvent = new AuditedDomainEvent() {
        };

        assertEquals("Principal", domainEvent.getPrincipalName());
        assertEquals(correlationId, domainEvent.getCorrelationId());
    }

    @Test
    public void testCreateEvent_NoContext() {
        AuditingContextHolder.clear();
        AuditedDomainEvent domainEvent = new AuditedDomainEvent() {
        };

        assertNull(domainEvent.getPrincipalName());
        assertNull(domainEvent.getCorrelationId());
    }

    @Test
    public void testCreateEvent_NoPrincipalInContext() {
        UUID correlationId = UUID.randomUUID();
        AuditingContextHolder.setContext(new AuditingContext(null, correlationId, "Command"));
        AuditedDomainEvent domainEvent = new AuditedDomainEvent(1, AggregateIdentifierFactory.randomIdentifier()) {
        };

        assertEquals(null, domainEvent.getPrincipalName());
        assertEquals(correlationId, domainEvent.getCorrelationId());
    }

}
