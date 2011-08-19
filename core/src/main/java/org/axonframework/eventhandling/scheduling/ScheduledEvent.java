/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventhandling.scheduling;

import org.axonframework.domain.ApplicationEvent;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Abstract implementation of the {@link ApplicationEvent} that contains the timestamp of the moment the event is
 * scheduled for publication.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class ScheduledEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1438418254163373484L;
    private static final String SCHEDULED_TIME_KEY = "scheduledTime";

    /**
     * Initializes a new scheduled event, scheduled for publication after a given <code>duration</code>.
     *
     * @param source   The instance considered the source of this event
     * @param duration The amount of time to wait before publication of the even
     */
    public ScheduledEvent(Object source, Duration duration) {
        this(source, new DateTime().plus(duration));
    }

    /**
     * Initializes a new scheduled event, scheduled for publication at the given <code>timestamp</code>.
     *
     * @param source    The instance considered the source of this event
     * @param timestamp The time at which to publish the event
     */
    public ScheduledEvent(Object source, DateTime timestamp) {
        super(source);
        addMetaData(SCHEDULED_TIME_KEY, timestamp);
    }

    /**
     * Initializes the event using given parameters. This constructor is intended for the reconstruction of exsisting
     * events (e.g. during deserialization).
     *
     * @param identifier        The identifier of the event
     * @param timestamp         The original creation timestamp
     * @param eventRevision     The revision of the event type
     * @param sourceDescription The description of the source. If <code>null</code>, will default to "[unknown
     *                          source]".
     * @param triggerDateTime   The timestamp the event is scheduled for triggering
     */
    protected ScheduledEvent(String identifier, DateTime timestamp, long eventRevision, String sourceDescription,
                             DateTime triggerDateTime) {
        super(identifier, timestamp, eventRevision, sourceDescription);
        addMetaData(SCHEDULED_TIME_KEY, triggerDateTime);
    }

    /**
     * The time at which the event is to be published. Note that this property should be immutable. If this value
     * changes the exact behavior is undefined.
     *
     * @return The time at which the event is to be published
     */
    public DateTime getScheduledTime() {
        return (DateTime) getMetaDataValue(SCHEDULED_TIME_KEY);
    }
}
