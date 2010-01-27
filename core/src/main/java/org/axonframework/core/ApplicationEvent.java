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

package org.axonframework.core;

import java.util.UUID;

/**
 * Represents an event that does not represent a state change of an application but does have functional meaning to the
 * application.
 * <p/>
 * This implementation will maintain a loose reference to the source of the event. However, this source will not be
 * serialized with the event. This means the source will not be available after deserialization. Instead, you can access
 * the {@link #getSourceType() source type} and {@link #getSourceDescription() source description}.
 * <p/>
 * <em>Note</em>: Do not confuse the type of reference used by this class with Java's weak reference. The reference
 * maintained by this event <em>will</em> prevent the garbage collector from cleaning up the source instance. However,
 * the reference will not survive serialization of the event.
 * <p/>
 * <em>Design consideration</em>: Be cautious when using application events. Make sure you did not overlook an
 * opportunity to use a domain event.
 *
 * @author Allard Buijze
 * @see org.axonframework.core.DomainEvent
 * @since 0.4
 */
public abstract class ApplicationEvent implements Event {

    private final UUID eventIdentifier;
    private final transient Object source;
    private final Class sourceType;
    private final String sourceDescription;

    /**
     * Initialize an application event with the given <code>source</code>. Source may be null. In that case, the source
     * type and source description will be set to <code>Object.class</code> and <code>[unknown source]</code>
     * respectively.
     *
     * @param source the instance that reported this event. If any.
     */
    protected ApplicationEvent(Object source) {
        this.eventIdentifier = UUID.randomUUID();
        this.source = source;
        if (source != null) {
            this.sourceType = source.getClass();
            this.sourceDescription = source.toString();
        } else {
            this.sourceDescription = "[unknown source]";
            this.sourceType = Object.class;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID getEventIdentifier() {
        return eventIdentifier;
    }

    /**
     * Returns the instance that reported this event. Will return null if no source was specified or if this event has
     * been serialized.
     *
     * @return the source of the event, if available.
     */
    public Object getSource() {
        return source;
    }

    /**
     * Returns the type of the instance that reported this event. Unlike the source, this value will survive
     * serialization. If the source was <code>null</code>, this method will return <code>Object.class</code>.
     *
     * @return the type of the instance that reported this event
     */
    public Class getSourceType() {
        return sourceType;
    }

    /**
     * Returns the description of the instance that reported this event. The description is set to the value returned by
     * <code>source.toString()</code>. Unlike the source itself, this value will survive serialization. If the source
     * was <code>null</code>, this method will return <code>[unknown source]</code>.
     *
     * @return the description of the instance that reported this event
     */
    public String getSourceDescription() {
        return sourceDescription;
    }
}
