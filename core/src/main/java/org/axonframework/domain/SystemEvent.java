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

package org.axonframework.domain;

import org.joda.time.DateTime;

/**
 * System events are a special type of application event. They notify the application of a state change of an
 * application component.
 * <p/>
 * In addition to the information provided by the {@link ApplicationEvent}, system events also provide information
 * about the exception that caused the event.
 *
 * @author Allard Buijze
 * @since 0.4
 */
public abstract class SystemEvent extends ApplicationEvent {

    private static final long serialVersionUID = 5636156978533165290L;

    private final Throwable cause;

    /**
     * Initialize a system event with the given <code>source</code>, and without an explicit <code>cause</code>.
     *
     * @param source The instance that reported this event. May be <code>null</code>.
     */
    protected SystemEvent(Object source) {
        this(source, null);
    }

    /**
     * Initialize a system event with the given <code>source</code> and <code>cause</code>.
     *
     * @param source The instance that reported this event. May be <code>null</code>.
     * @param cause  The exception that cause this event to be dispatched
     */
    protected SystemEvent(Object source, Throwable cause) {
        super(source);
        this.cause = cause;
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
     * @param cause             The cause exception if this event represents an error
     */
    protected SystemEvent(String identifier, DateTime timestamp, long eventRevision, String sourceDescription,
                          Throwable cause) {
        super(identifier, timestamp, eventRevision, sourceDescription);
        this.cause = cause;
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
     */
    protected SystemEvent(String identifier, DateTime timestamp, long eventRevision, String sourceDescription) {
        this(identifier, timestamp, eventRevision, sourceDescription, null);
    }

    /**
     * Returns the cause that was attached to this event. Can return <code>null</code>.
     *
     * @return the cause that was attached to this event. Can return <code>null</code>.
     */
    public Throwable getCause() {
        return cause;
    }
}
