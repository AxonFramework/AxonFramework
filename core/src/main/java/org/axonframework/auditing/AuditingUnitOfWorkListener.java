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

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.Event;
import org.axonframework.domain.MutableEventMetaData;
import org.axonframework.unitofwork.UnitOfWorkListener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Listener implementation that adds auditing information to events being tracked by the unit of work this listener is
 * registered to.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AuditingUnitOfWorkListener implements UnitOfWorkListener {

    private final AuditDataProvider auditDataProvider;
    private final AuditLogger auditLogger;
    private Object command;
    private List<Event> recordedEvents = new ArrayList<Event>();

    /**
     * Initialize a listener for the given <code>command</code>. The <code>auditDataProvider</code> is called before the
     * Unit Of Work is committed to provide the auditing information. The <code>auditLogger</code> is invoked after the
     * Unit Of Work is successfully committed.
     *
     * @param command           The command being audited
     * @param auditDataProvider The instance providing the information to attach to the events
     * @param auditLogger       The logger writing the audit
     */
    public AuditingUnitOfWorkListener(Object command, AuditDataProvider auditDataProvider, AuditLogger auditLogger) {
        this.auditDataProvider = auditDataProvider;
        this.auditLogger = auditLogger;
        this.command = command;
    }

    @Override
    public void afterCommit() {
        auditLogger.append(command, recordedEvents);
    }

    @Override
    public void onRollback() {
    }

    @Override
    public void onPrepareCommit(Set<AggregateRoot> aggregateRoots, List<Event> events) {
        Map<String, Serializable> auditData = auditDataProvider.provideAuditDataFor(command);
        collectEvents(aggregateRoots);
        events.addAll(events);
        injectAuditData(auditData);
    }

    private void collectEvents(Set<AggregateRoot> aggregateRoots) {
        for (AggregateRoot aggregateRoot : aggregateRoots) {
            DomainEventStream domainEventStream = aggregateRoot.getUncommittedEvents();
            while (domainEventStream.hasNext()) {
                recordedEvents.add(domainEventStream.next());
            }
        }
    }

    private void injectAuditData(Map<String, Serializable> auditData) {
        for (Event event : recordedEvents) {
            MutableEventMetaData eventMetaData = ((MutableEventMetaData) event.getMetaData());
            for (Map.Entry<String, Serializable> entry : auditData.entrySet()) {
                eventMetaData.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
