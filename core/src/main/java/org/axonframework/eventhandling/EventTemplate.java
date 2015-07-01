/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.correlation.CorrelationDataHolder;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.MetaData;

import java.util.Collections;
import java.util.Map;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;

/**
 * Template class that dispatches Events to an {@link org.axonframework.eventhandling.EventBus}, taking active
 * UnitOfWork into account. When a UnitOfWork is active, the template will ensure correct ordering with any other
 * events published during the course of that UnitOfWork. If no unit of work is active, the Event is published
 * immediately to the Event Bus.
 * <p/>
 * Messages sent with this template will have the current thread's correlation data in its meta data.
 * <p/>
 * The template also allows addition of Meta Data properties to all Events sent through this template.
 *
 * @author Allard Buijze
 * @see org.axonframework.correlation.CorrelationDataHolder
 * @since 2.2
 */
@Deprecated
public class EventTemplate {

    private final EventBus eventBus;
    private final MetaData additionalMetaData;

    /**
     * Initialize the template to publish Events through the given <code>eventBus</code>.
     *
     * @param eventBus The EventBus to publish Event Messages on.
     */
    public EventTemplate(EventBus eventBus) {
        this(eventBus, Collections.<String, Object>emptyMap());
    }

    /**
     * Initialize the template to publish Events through the given <code>eventBus</code>. The given
     * <code>additionalMetaData</code> is attached to all events published through this instance.
     *
     * @param eventBus           The EventBus to publish Event Messages on.
     * @param additionalMetaData The meta data to add to messages sent
     */
    public EventTemplate(EventBus eventBus, Map<String, ?> additionalMetaData) {
        this.eventBus = eventBus;
        this.additionalMetaData = MetaData.from(additionalMetaData);
    }

    /**
     * Publishes an event with given <code>payload</code> on the configured Event Bus.
     * <p/>
     * This method takes into account that a Unit of Work may be active, using that Unit of Work to ensure
     * correct publication of the event.
     *
     * @param payload The payload of the event to publish
     */
    public void publishEvent(Object payload) {
        doPublish(createEventMessage(payload));
    }

    /**
     * Publishes an event with given <code>payload</code> on the configured Event Bus, with given <code>metaData</code>
     * attached. If meta data is also configured on this template, the given <code>metaData</code> will override any
     * existing entries under the same key.
     * <p/>
     * This method takes into account that a Unit of Work may be active, using that Unit of Work to ensure
     * correct publication of the event.
     *
     * @param payload  The payload of the event to publish
     * @param metaData Additional meta data to attach to the event
     */
    public void publishEvent(Object payload, Map<String, ?> metaData) {
        doPublish(createEventMessage(payload).andMetaData(metaData));
    }

    private void doPublish(EventMessage<?> message) {
        eventBus.publish(message);
    }

    private EventMessage<Object> createEventMessage(Object payload) {
        final EventMessage<Object> eventMessage = asEventMessage(payload);
        return eventMessage.andMetaData(CorrelationDataHolder.getCorrelationData())
                           .andMetaData(eventMessage.getMetaData())
                           .andMetaData(additionalMetaData);
    }
}
