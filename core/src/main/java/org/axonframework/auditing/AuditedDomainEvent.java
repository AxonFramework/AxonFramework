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

import org.axonframework.domain.DomainEvent;

import java.util.UUID;

/**
 * Special implementation of the {@link DomainEvent} that stores auditing information inside the event, if present. If
 * no auditing context is present, both the correlation ID and the principal name will be set to <code>null</code>.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class AuditedDomainEvent extends DomainEvent implements Audited {

    private final UUID correlationId;
    private final String principalName;

    public AuditedDomainEvent() {
        correlationId = extractCorrelationId();
        principalName = extractPrincipalName();
    }

    public AuditedDomainEvent(long sequenceNumber, UUID aggregateIdentifier) {
        super(sequenceNumber, aggregateIdentifier);
        correlationId = extractCorrelationId();
        principalName = extractPrincipalName();
    }

    private UUID extractCorrelationId() {
        AuditingContext context = AuditingContextHolder.currentAuditingContext();
        if (context == null) {
            return null;
        }
        return context.getCorrelationId();
    }

    private String extractPrincipalName() {
        AuditingContext context = AuditingContextHolder.currentAuditingContext();
        if (context == null || context.getPrincipal() == null) {
            return null;
        }
        return context.getPrincipal().getName();
    }

    @Override
    public UUID getCorrelationId() {
        return correlationId;
    }

    @Override
    public String getPrincipalName() {
        return principalName;
    }
}
